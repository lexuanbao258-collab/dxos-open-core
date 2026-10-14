package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WORKFLOW_AUDIT_LOCAL_JDBC_URL", matches=".+")
class WorkflowSchemaBootstrapIntegrationTest {
    @TempDir Path temporary;
    Map<String,String> environment;
    Path bundle;
    String localUrl;
    String probe = "wf25_bootstrap_" + UUID.randomUUID().toString().replace("-", "");
    List<String> writes = new ArrayList<>();
    Set<String> createdRoles = new HashSet<>();
    boolean runtimeLoginChanged;
    boolean originalRuntimeLogin;
    static final String S = WorkflowSchemaBootstrap.SCHEMA;

    @BeforeEach void setup() throws Exception {
        localUrl = WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_JDBC_URL");
        if (!localUrl.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/postgres"))
            throw new IllegalArgumentException("Disposable loopback database required");
        localUrl = localUrl.replace("127.0.0.1", "localhost");
        environment = new HashMap<>(Map.of("DB_HOST", WorkflowSchemaBootstrap.HOST, "DB_PORT", "5432",
                "DB_NAME", "postgres", "DB_USER", "postgres",
                "DB_PASSWORD", WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_DB_PASSWORD"),
                "PGSSLROOTCERT", WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_CA_CERT"),
                "WORKFLOW_SCHEMA_BOOTSTRAP_APPROVAL", S));
        bundle = Files.createDirectory(temporary.resolve("bundle"));
        for (String name : List.of("V3__create_workflow_persistence.sql", "V4__seed_workflow_definitions.sql"))
            Files.copy(Path.of("src/main/resources/db/migration", name), bundle.resolve(name));
        try (Connection c = connect(WorkflowSchemaBootstrap.properties(environment)); var statement = c.createStatement()) {
            try (var rows = statement.executeQuery("SELECT pg_catalog.current_setting('dxos.wf25_disposable',true)")) {
                requireFixture(rows.next() && "on".equals(rows.getString(1)));
            }
            c.setReadOnly(false);
            c.setAutoCommit(true);
            statement.execute("BEGIN READ WRITE");
            for(String role:List.of("dxos_wf25_migrator","dxos_wf25_runtime")){
                boolean exists;
                try(var rows=statement.executeQuery("SELECT rolcanlogin FROM pg_catalog.pg_roles WHERE rolname='"+role+"'")){
                    exists=rows.next();
                    if(exists && role.equals("dxos_wf25_runtime"))originalRuntimeLogin=rows.getBoolean(1);
                }
                if(!exists){statement.execute("CREATE ROLE "+role+" NOLOGIN");createdRoles.add(role);}
            }
            statement.execute("COMMIT");
        }
    }

    @AfterEach void cleanup() throws Exception {
        try (Connection c = connect(WorkflowSchemaBootstrap.properties(environment)); var statement = c.createStatement()) {
            c.setReadOnly(false);
            statement.execute("BEGIN READ WRITE");
            statement.execute("DROP EVENT TRIGGER IF EXISTS " + probe);
            statement.execute("DROP FUNCTION IF EXISTS public." + probe + "()");
            statement.execute("DROP SCHEMA IF EXISTS " + S + " CASCADE");
            statement.execute("DROP TABLE IF EXISTS public." + probe);
            if(runtimeLoginChanged && !createdRoles.contains("dxos_wf25_runtime"))
                statement.execute("ALTER ROLE dxos_wf25_runtime "+(originalRuntimeLogin?"LOGIN":"NOLOGIN"));
            for(String role:createdRoles)statement.execute("DROP ROLE "+role);
            statement.execute("COMMIT");
        }
    }

    @Test void commitsOnlyOneEmptyPrivateSchemaAndAuditsOnNewReadOnlyConnection() throws Exception {
        var guard = mock(WorkflowEventTriggerGuard.class);
        var opens = new AtomicInteger();
        var result = WorkflowSchemaBootstrap.run(environment,bundle,(url,properties) -> {
            assertThat(url).isEqualTo(WorkflowSchemaBootstrap.URL);
            opens.incrementAndGet(); return record(connect(properties), false, false);
        },guard);
        assertThat(result.status()).isEqualTo("COMMITTED_PASS");
        assertThat(result.postcommit()).isEqualTo("PASS");
        assertThat(result.audit().valid()).isTrue();
        assertThat(result.direct() && result.tls()).isTrue();
        assertThat(opens).hasValue(2);
        assertThat(writes.stream().filter(sql -> sql.startsWith("CREATE"))).containsExactly("CREATE SCHEMA dxos_wf25_smoke_01 AUTHORIZATION postgres");
        assertThat(writes).allMatch(sql -> sql.equals("LISTEN pgrst") || sql.equals("BEGIN READ WRITE") || sql.startsWith("SET LOCAL ") || sql.equals("CREATE SCHEMA dxos_wf25_smoke_01 AUTHORIZATION postgres"));
        verify(guard,times(2)).verifyReadOnly(any(),eq(S));
        verify(guard).verify(any(),eq(S));
        assertThat(schemaPresent()).isTrue();
    }

    @Test void existingSchemaStopsWithoutAnySchemaWrite() throws Exception {
        execute("CREATE SCHEMA " + S + " AUTHORIZATION postgres");
        var result = run(mock(WorkflowEventTriggerGuard.class));
        assertThat(result.status()).isEqualTo("NOT_EXECUTED");
        assertThat(writes).containsExactly("LISTEN pgrst");
        assertThat(schemaPresent()).isTrue();
    }

    @Test void loginCustomRoleStopsBeforeWrite() throws Exception {
        execute("ALTER ROLE dxos_wf25_runtime LOGIN");
        runtimeLoginChanged=true;
        assertThat(run(mock(WorkflowEventTriggerGuard.class)).status()).isEqualTo("NOT_EXECUTED");
        assertThat(schemaPresent()).isFalse();
    }

    @Test void manifestDriftStopsBeforeWrite() throws Exception {
        var guard = mock(WorkflowEventTriggerGuard.class);
        doThrow(new SQLException("catalog drift", "42501")).when(guard).verifyReadOnly(any(),anyString());
        assertThat(run(guard).status()).isEqualTo("NOT_EXECUTED");
        assertThat(schemaPresent()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"wrong-ca","wrong-hostname"})
    void actualTlsFailuresPreventWrite(String failure) throws Exception {
        var changed = new HashMap<>(environment);
        if (failure.equals("wrong-ca")) changed.put("PGSSLROOTCERT", WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_WRONG_CA_CERT"));
        else localUrl = localUrl.replace("localhost", "127.0.0.1");
        var result = WorkflowSchemaBootstrap.run(changed,bundle,(url,p) -> connect(p),mock(WorkflowEventTriggerGuard.class));
        assertThat(result.status()).isEqualTo("NOT_EXECUTED");
        assertThat(result.tls()).isFalse();
        localUrl = localUrl.replace("127.0.0.1", "localhost");
        assertThat(schemaPresent()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"public-acl","unexpected-table","outside-catalog"})
    void postcheckFailureRollsBackSchemaAndTriggerEffects(String failure) throws Exception {
        watcher(switch(failure) {
            case "public-acl" -> "GRANT USAGE ON SCHEMA " + S + " TO PUBLIC;";
            case "unexpected-table" -> "CREATE TABLE " + S + ".flyway_schema_history(id int);";
            default -> "CREATE TABLE public." + probe + "(id int);";
        });
        var result = run(mock(WorkflowEventTriggerGuard.class));
        assertThat(result.status()).isEqualTo("ROLLED_BACK");
        assertThat(result.postcommit()).isEqualTo("NOT_RUN");
        assertThat(schemaPresent()).isFalse();
        assertThat(scalar("SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relname='"+probe+"'")).isZero();
    }

    @Test void observesOnlyOwnPgrstNotificationAfterCommit() throws Exception {
        watcher("PERFORM pg_catalog.pg_notify('pgrst','reload schema');");
        var result = run(mock(WorkflowEventTriggerGuard.class));
        assertThat(result.status()).isEqualTo("COMMITTED_PASS");
        assertThat(result.notification()).isEqualTo("OBSERVED_OWN_SESSION");
    }

    @ParameterizedTest @ValueSource(strings={"before","after"})
    void lostCommitResponseReconcilesWithoutRetryOrDrop(String when) throws Exception {
        var opens = new AtomicInteger();
        var result = WorkflowSchemaBootstrap.run(environment,bundle,(url,p) -> {
            Connection c=connect(p);
            return opens.incrementAndGet()==1 ? record(c,true,when.equals("after")) : record(c,false,false);
        },mock(WorkflowEventTriggerGuard.class));
        assertThat(result.status()).isEqualTo("COMMIT_UNKNOWN");
        assertThat(opens).hasValue(2);
        assertThat(schemaPresent()).isEqualTo(when.equals("after"));
        assertThat(result.postcommit()).isEqualTo(when.equals("after") ? "PASS" : "FAIL");
        var report = new ByteArrayOutputStream();
        result.report(new PrintStream(report));
        assertThat(report.toString()).contains("OTHER_CLOUD_WRITES=" + (when.equals("after")
                ? "NONE" : "UNVERIFIED_POSTCOMMIT_AUDIT_NOT_CONFIRMED"));
        assertThat(writes.stream().filter(sql->sql.startsWith("CREATE"))).hasSize(1);
    }

    @Test void independentAuditFailureNeverReturnsOverallSuccess() throws Exception {
        var opens=new AtomicInteger();
        var result=WorkflowSchemaBootstrap.run(environment,bundle,(url,p)->{
            if(opens.incrementAndGet()==2)throw new SQLException("private driver failure", "08006");
            return record(connect(p),false,false);
        },mock(WorkflowEventTriggerGuard.class));
        assertThat(result.status()).isEqualTo("COMMITTED_PASS");
        assertThat(result.postcommit()).isEqualTo("FAIL");
        var report = new ByteArrayOutputStream();
        assertThat(result.report(new PrintStream(report))).isEqualTo(1);
        assertThat(report.toString()).contains("OTHER_CLOUD_WRITES=UNVERIFIED_POSTCOMMIT_AUDIT_NOT_CONFIRMED");
        assertThat(schemaPresent()).isTrue();
    }

    private WorkflowSchemaBootstrap.Result run(WorkflowEventTriggerGuard guard) {
        return WorkflowSchemaBootstrap.run(environment,bundle,(url,p)->record(connect(p),false,false),guard);
    }
    private Connection connect(Properties properties) throws SQLException {
        return DriverManager.getConnection(localUrl,properties);
    }
    private Connection record(Connection delegate,boolean commitFault,boolean applyCommit) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("commit") && commitFault){
                if(applyCommit)delegate.commit();
                throw new SQLException("Lost COMMIT reply", "08006");
            }
            Object value=invoke(method,delegate,args);
            if(method.getName().equals("createStatement")){
                var statement=(Statement)value;
                return Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class<?>[]{Statement.class},(p,m,a)->{
                    if(m.getName().equals("execute") && a!=null && a[0] instanceof String sql)writes.add(sql);
                    return invoke(m,statement,a);
                });
            }
            return value;
        });
    }
    private static Object invoke(Method method,Object target,Object[] args)throws Throwable{
        try{return method.invoke(target,args);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    private void watcher(String body)throws Exception{
        execute("CREATE FUNCTION public."+probe+"() RETURNS event_trigger LANGUAGE plpgsql AS $$ BEGIN "+body+" END $$");
        execute("CREATE EVENT TRIGGER "+probe+" ON ddl_command_end WHEN TAG IN ('CREATE SCHEMA') EXECUTE FUNCTION public."+probe+"()");
    }
    private void execute(String sql)throws Exception{
        try(Connection c=connect(WorkflowSchemaBootstrap.properties(environment));var s=c.createStatement()){
            c.setReadOnly(false);s.execute("BEGIN READ WRITE");s.execute(sql);s.execute("COMMIT");
        }
    }
    private boolean schemaPresent()throws Exception{return scalar("SELECT count(*) FROM pg_namespace WHERE nspname='"+S+"'")==1;}
    private long scalar(String sql)throws Exception{
        try(Connection c=connect(WorkflowSchemaBootstrap.properties(environment));var s=c.createStatement();var r=s.executeQuery(sql)){
            r.next();return r.getLong(1);
        }
    }
    private static void requireFixture(boolean safe){if(!safe)throw new IllegalArgumentException("Disposable marker required");}
}
