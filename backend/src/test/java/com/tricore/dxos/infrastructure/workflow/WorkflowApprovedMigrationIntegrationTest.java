package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WORKFLOW_AUDIT_LOCAL_JDBC_URL",matches=".+")
class WorkflowApprovedMigrationIntegrationTest {
    @TempDir Path temporary;
    Map<String,String> env;
    Path bundle;
    String url;
    Set<String> createdRoles=new HashSet<>();
    List<String> writes=new ArrayList<>();
    String fault="";
    boolean handedOff;
    String probe="wf25_migration_"+UUID.randomUUID().toString().replace("-","");

    @BeforeEach void setup() throws Exception {
        url=WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_JDBC_URL");
        if(!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/postgres"))
            throw new IllegalArgumentException("Disposable loopback required");
        url=url.replace("127.0.0.1","localhost");
        env=new HashMap<>(Map.of("DB_HOST",WorkflowSchemaBootstrap.HOST,"DB_PORT","5432","DB_NAME","postgres",
            "DB_USER","postgres","DB_PASSWORD",WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_DB_PASSWORD"),
            "PGSSLROOTCERT",WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_CA_CERT"),
            "WORKFLOW_FLYWAY_APPROVAL",WorkflowApprovedMigration.S));
        bundle=Files.createDirectory(temporary.resolve("bundle"));
        for(String name:List.of("V3__create_workflow_persistence.sql","V4__seed_workflow_definitions.sql"))
            Files.copy(Path.of("src/main/resources/db/migration",name),bundle.resolve(name));
        try(Connection c=connect(WorkflowApprovedMigration.properties(env,true));var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT pg_catalog.current_setting('dxos.wf25_disposable',true)")) {
                if(!r.next() || !"on".equals(r.getString(1)))throw new IllegalArgumentException("Disposable marker missing");
            }
            for(String role:List.of("dxos_wf25_migrator","dxos_wf25_runtime")) {
                try(var r=s.executeQuery("SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='"+role+"'")) {
                    if(!r.next())createdRoles.add(role);
                }
                if(createdRoles.contains(role))s.execute("CREATE ROLE "+role+" NOLOGIN");
            }
            s.execute("CREATE SCHEMA "+WorkflowApprovedMigration.S+" AUTHORIZATION postgres");
            s.execute("CREATE TABLE public."+probe+"(id text)");
            s.execute("INSERT INTO public."+probe+" VALUES('preserved')");
        }
    }
    @AfterEach void cleanup() throws Exception {
        try(Connection c=connect(WorkflowApprovedMigration.properties(env,true));var s=c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS "+WorkflowApprovedMigration.S+" CASCADE");
            s.execute("DROP TABLE IF EXISTS public."+probe);
            s.execute("DROP TABLE IF EXISTS public."+probe+"_drift");
            for(String role:createdRoles)s.execute("DROP ROLE "+role);
        }
    }
    private Connection connect(Properties p) throws SQLException {return DriverManager.getConnection(url,p);}
    private WorkflowApprovedMigration.Result run() {
        return WorkflowApprovedMigration.run(env,bundle,(ignored,p)-> {
            assertThat(ignored).isEqualTo(WorkflowApprovedMigration.URL);
            if(handedOff && p.getProperty("options").contains("default_transaction_read_only")
                    && fault.equals("audit-connect"))throw new SQLException("private audit failure","08006");
            return record(connect(p));
        },mock(WorkflowEventTriggerGuard.class),f -> {
            handedOff=true;
            if(fault.equals("v3-rollback"))throw new SQLException("confirmed SQL failure","23514");
            f.migrate();
            if(fault.equals("outside-drift"))execute("CREATE TABLE public."+probe+"_drift(id int)");
            if(fault.equals("inventory-drift"))execute("ALTER TABLE "+WorkflowApprovedMigration.S+".workflow_instances ADD COLUMN unapproved int");
            if(fault.equals("column-acl"))execute("GRANT SELECT(instance_id) ON "+WorkflowApprovedMigration.S+".workflow_instances TO PUBLIC");
            if(fault.equals("seed-drift"))execute("INSERT INTO "+WorkflowApprovedMigration.S+".workflow_instances(instance_id,definition_id,definition_version,resource_type,resource_id,current_state,lifecycle,created_at_seconds,created_at_nanos,updated_at_seconds,updated_at_nanos) VALUES('unapproved','document-review',1,'test','test','DRAFT','NOT_STARTED',0,0,0,0)");
        });
    }
    @Test void developmentSmokeAuditPreservesCatalogAndDetectsSeedAndInventoryDrift() throws Exception {
        assertThat(run().status()).isEqualTo("COMMITTED_PASS");
        var development = new HashMap<>(env);
        var endpoint = java.net.URI.create(url.substring(5));
        development.put("DB_HOST", "localhost");
        development.put("DB_PORT", Integer.toString(endpoint.getPort()));
        development.put("WORKFLOW_DEV_INTEGRATION_OPT_IN", WorkflowDevelopmentSmokeRunner.OPT_IN);
        development.put("WORKFLOW_DEV_APPROVED_SCHEMA", WorkflowSmokeRunner.SCHEMA);
        development.put("WORKFLOW_DEV_BUNDLE", bundle.toString());
        development.put("WORKFLOW_DEV_LOCAL_DISPOSABLE", "true");
        development.put("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE", "true");
        development.put("WF25_DISPOSABLE_TEST", "1");
        var audit = WorkflowDevelopmentSmokeRunner.prepare(development, "audit");
        var before = audit.audit("development-audit");
        assertThat(before.instances()).isZero();
        assertThat(before.history()).isZero();
        development.put("WORKFLOW_DEV_WRITE_APPROVAL", WorkflowSmokeRunner.SCHEMA);
        WorkflowDevelopmentSmokeRunner.prepare(development, "smoke").smoke("development-audit");
        var after = audit.audit("development-audit");
        assertThat(after.catalog()).isEqualTo(before.catalog());
        assertThat(after.instances()).isEqualTo(2);
        assertThat(after.runInstances()).isEqualTo(2);
        assertThat(after.history()).isEqualTo(4);
        assertThat(after.runHistory()).isEqualTo(4);
        assertThat(audit.audit("another-run").runInstances()).isZero();
        WorkflowDevelopmentSmokeRunner.prepare(development, "read").read("development-audit");
        execute("ALTER TABLE " + WorkflowSmokeRunner.SCHEMA + ".workflow_instances ADD COLUMN unexpected int");
        assertThatThrownBy(() -> audit.audit("development-audit")).hasMessage("Inventory drift");
        execute("ALTER TABLE " + WorkflowSmokeRunner.SCHEMA + ".workflow_instances DROP COLUMN unexpected");
        execute("INSERT INTO " + WorkflowSmokeRunner.SCHEMA + ".workflow_definitions(definition_id,definition_version,initial_state) VALUES('extra',1,'DRAFT');"
                + "INSERT INTO " + WorkflowSmokeRunner.SCHEMA + ".workflow_definition_states VALUES('extra',1,'DRAFT')");
        assertThatThrownBy(() -> audit.audit("development-audit")).hasMessage("Seed drift");
    }

    @Test void realFlywayRunsOnlyV3V4WithExactInventoryAndFreshReadOnlyAudit() throws Exception {
        var result=run();
        result.report(System.out);
        try(Connection c=connect(WorkflowApprovedMigration.properties(env,false))) {
            WorkflowSchemaBootstrap.beginReadOnly(c);
            System.out.println("LOCAL_APPROVED_INVENTORY="+WorkflowApprovedMigration.inventory(c));
            assertThat(c.getSchema()).isEqualTo(WorkflowApprovedMigration.S);
            c.rollback();
        }
        assertThat(result.status()).isEqualTo("COMMITTED_PASS");
        assertThat(result.audit()).isEqualTo("PASS");
        assertThat(result.inventory()).isEqualTo("PASS");
        assertThat(result.seed()).isEqualTo("PASS");
        assertThat(result.history().ready()).isTrue();
        assertThat(result.outside()).isEqualTo("NONE");
        assertThat(result.direct() && result.tls()).isTrue();
        assertThat(writes).noneMatch(sql->sql.stripLeading().toUpperCase(Locale.ROOT).matches("(?s)^(CREATE SCHEMA|DROP |ALTER ROLE|GRANT ).*"));
        assertThat(scalar("SELECT count(*) FROM public."+probe+" WHERE id='preserved'")).isEqualTo(1);
    }
    @Test void failedGuardAfterRealConnectionReportsTlsAndSafeFailureStage() {
        var guard=mock(WorkflowEventTriggerGuard.class);
        try {
            doThrow(new SQLException("password=private-do-not-print jdbc:postgresql://private-host","42501"))
                    .when(guard).verifyReadOnly(any(),anyString());
        } catch(SQLException impossible) {throw new AssertionError(impossible);}
        var result=WorkflowApprovedMigration.run(env,bundle,(u,p)->record(connect(p)),guard,
                f->{throw new AssertionError("Migration must not run");});
        assertThat(result.status()).isEqualTo("NOT_EXECUTED");
        assertThat(result.direct()).isTrue();
        assertThat(result.tls()).isTrue();
        assertThat(result.phase()).isEqualTo("event_catalog_guard");
        assertThat(result.failureCode()).isEqualTo("EVENT_CATALOG_GUARD_REJECTED");
        assertThat(result.sqlState()).isEqualTo("42501");
        var out=new java.io.ByteArrayOutputStream();
        result.report(new java.io.PrintStream(out));
        assertThat(out.toString()).doesNotContain("private-do-not-print","private-host","jdbc:postgresql:");
    }
    @Test void originalWrongReadOnlyPathIsDiagnosedBeforeGuardOrFlyway() {
        var guard=mock(WorkflowEventTriggerGuard.class);
        var result=WorkflowApprovedMigration.run(env,bundle,(u,p)->{
            p.setProperty("currentSchema","pg_catalog,pg_temp");
            return record(connect(p));
        },guard,f->{throw new AssertionError("Migration must not run");});
        assertThat(result.status()).isEqualTo("NOT_EXECUTED");
        assertThat(result.direct() && result.tls()).isTrue();
        assertThat(result.phase()).isEqualTo("read_only_search_path");
        assertThat(result.failureCode()).isEqualTo("SEARCH_PATH_MISMATCH");
        assertThat(result.sqlState()).isEqualTo("NONE");
        verifyNoInteractions(guard);
    }
    @ParameterizedTest @ValueSource(strings={"existing-table","public-acl","custom-login","bundle"})
    void precheckFailuresNeverHandOffToFlyway(String failure) throws Exception {
        switch(failure) {
            case "existing-table" -> execute("CREATE TABLE "+WorkflowApprovedMigration.S+".unexpected(id int)");
            case "public-acl" -> execute("GRANT USAGE ON SCHEMA "+WorkflowApprovedMigration.S+" TO PUBLIC");
            case "custom-login" -> execute("ALTER ROLE dxos_wf25_runtime LOGIN");
            default -> Files.writeString(bundle.resolve("V4__seed_workflow_definitions.sql"),"changed");
        }
        try {
            assertThat(run().status()).isEqualTo("NOT_EXECUTED");
            assertThat(handedOff).isFalse();
            assertThat(scalar("SELECT count(*) FROM pg_catalog.pg_class WHERE relnamespace='"+WorkflowApprovedMigration.S+"'::regnamespace AND relname='flyway_schema_history'")).isZero();
        } finally {if(failure.equals("custom-login"))execute("ALTER ROLE dxos_wf25_runtime NOLOGIN");}
    }
    @ParameterizedTest @ValueSource(strings={"wrong-ca","wrong-host"})
    void tlsCannotDowngradeToReachMigrations(String failure) throws Exception {
        if(failure.equals("wrong-ca"))env.put("PGSSLROOTCERT",WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_WRONG_CA_CERT"));
        else url=url.replace("localhost","127.0.0.1");
        var result=run();
        assertThat(result.status()).isEqualTo("NOT_EXECUTED");
        assertThat(result.tls()).isFalse();
        assertThat(handedOff).isFalse();
        url=url.replace("127.0.0.1","localhost");
        env.put("PGSSLROOTCERT",WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_CA_CERT"));
    }
    @Test void v3CommittedAndV4FailsWithoutRetryOrDestructiveRecovery() {
        fault="v4-error";
        var result=run();
        assertThat(result.status()).isEqualTo("PARTIAL_COMMIT");
        assertThat(result.history().state()).isEqualTo("V3_ONLY_SUCCESS");
        assertThat(result.history().v4()).isEqualTo("ABSENT");
        assertThat(result.inventory()).isEqualTo("PASS");
        assertThat(result.seed()).isEqualTo("FAIL");
        assertThat(writes.stream().filter(WorkflowApprovedMigrationIntegrationTest::seedInsert)).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings={"commit-before","commit-after"})
    void lostV4CommitResponseReconcilesSeedAndHistoryWithoutRetry(String failure) {
        fault=failure;
        var result=run();
        result.report(System.out);
        assertThat(result.status()).isEqualTo("COMMIT_UNKNOWN");
        assertThat(result.history().v3()).isEqualTo("SUCCESS_PIN_MATCH");
        assertThat(result.seed()).isEqualTo(failure.equals("commit-after")?"PASS":"FAIL");
        assertThat(result.audit()).isEqualTo(result.history().ready()?"PASS":"FAIL");
        assertThat(writes.stream().filter(WorkflowApprovedMigrationIntegrationTest::seedInsert)).hasSize(1);
    }
    @Test void confirmedSqlFailureWithNoWorkflowObjectsReportsRolledBack() {
        fault="v3-rollback";
        var result=run();
        assertThat(result.status()).isEqualTo("FAILED_ROLLED_BACK");
        assertThat(result.history().noRows()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"outside-drift","inventory-drift","column-acl","seed-drift","audit-connect"})
    void independentAuditFailureCannotReturnSuccess(String failure) {
        fault=failure;
        var result=run();
        assertThat(result.status()).isNotEqualTo("COMMITTED_PASS");
        assertThat(result.audit()).isEqualTo("FAIL");
        if(failure.equals("outside-drift"))assertThat(result.outside()).startsWith("CATALOG_DRIFT_");
        if(failure.equals("inventory-drift"))assertThat(result.inventory()).isEqualTo("FAIL");
        if(failure.equals("column-acl"))assertThat(result.inventory()).isEqualTo("FAIL");
        if(failure.equals("seed-drift"))assertThat(result.seed()).isEqualTo("FAIL");
        if(failure.equals("audit-connect"))assertThat(result.status()).isEqualTo("COMMIT_UNKNOWN");
    }
    private Connection record(Connection c) {
        boolean[] seedOnThisConnection={false};
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("commit") && seedOnThisConnection[0] && (fault.equals("commit-before") || fault.equals("commit-after"))) {
                if(fault.equals("commit-after"))c.commit();
                throw new SQLException("Lost private COMMIT reply","08006");
            }
            Object result=invoke(m,c,a);
            if(result instanceof Statement s && (m.getName().equals("createStatement") || m.getName().equals("prepareStatement"))) {
                var interfaces=result instanceof PreparedStatement?new Class<?>[]{PreparedStatement.class}:new Class<?>[]{Statement.class};
                String prepared=m.getName().equals("prepareStatement")?(String)a[0]:null;
                return Proxy.newProxyInstance(Statement.class.getClassLoader(),interfaces,(p2,m2,a2)->{
                    String sql=a2!=null && a2.length>0 && a2[0] instanceof String?(String)a2[0]:prepared;
                    if(m2.getName().startsWith("execute") && sql!=null) {
                        if(!sql.stripLeading().startsWith("SELECT"))writes.add(sql);
                        if(seedInsert(sql)) {
                            seedOnThisConnection[0]=true;
                            if(fault.equals("v4-error"))throw new SQLException("V4 SQL fault","23514");
                        }
                    }
                    return invoke(m2,s,a2);
                });
            }
            return result;
        });
    }
    private static Object invoke(Method m,Object target,Object[] args) throws Throwable {
        try{return m.invoke(target,args);}catch(InvocationTargetException ex){throw ex.getCause();}
    }
    private static boolean seedInsert(String sql) {
        return sql.contains("INSERT INTO") && sql.contains("workflow_definitions") && sql.contains("document-review");
    }
    private void execute(String sql) throws Exception {
        try(Connection c=connect(WorkflowApprovedMigration.properties(env,true));var s=c.createStatement()){s.execute(sql);}
    }
    private long scalar(String sql) throws Exception {
        try(Connection c=connect(WorkflowApprovedMigration.properties(env,false));var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}
    }
}
