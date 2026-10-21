package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tricore.dxos.application.authorization.*;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import com.tricore.dxos.core.workflow.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Real LOGIN, six D3 ports, NOLOGIN owner and guarded-adapter faults; disposable 55427 only. */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):55427/stage6_disposable_test")
class Stage6RuntimeLoginPrivilegeIntegrationTest {
    private static final String INSTANCE_ID = "wf_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private Connection administrator;
    private String schema, runtimeRole, ownerRole, runtimePassword, jdbcUrl;
    private DriverManagerDataSource database;
    private static final ResourceReference RESOURCE = new ResourceReference("item", "res-a");
    private static final Instant CREATED = Instant.ofEpochSecond(10,123);
    private static final Instant ACTIVATED = Instant.ofEpochSecond(11,456);
    private static final Instant FINISHED = Instant.ofEpochSecond(12,789);

    @BeforeEach void prepare() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")));
        assumeTrue("YES".equals(System.getenv("STAGE6_LOGIN_ROLE_TEST_APPROVAL")));
        assumeTrue("true".equals(System.getenv("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE")));
        jdbcUrl = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        String adminUser = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        assumeTrue("stage6_test".equals(adminUser));
        administrator = DriverManager.getConnection(jdbcUrl, adminUser, System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD"));
        administrator.setAutoCommit(false);
        assertThat(number("SHOW server_version_num")).isBetween(170000,179999);
        assertThat(text("SELECT current_database()")).isEqualTo("stage6_disposable_test");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        schema = "s6_login_" + suffix;
        runtimeRole = "s6_rt_" + suffix;
        ownerRole = "s6_owner_" + suffix;
        byte[] entropy = new byte[24];
        new SecureRandom().nextBytes(entropy);
        runtimePassword = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        sql("CREATE ROLE " + ownerRole + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT");
        sql("CREATE SCHEMA " + schema);
        for (String file : List.of("src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_read_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_create_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_actions_candidate.sql")) {
            sql(Files.readString(Path.of(file)).replace("${flyway:defaultSchema}", schema));
        }
        sql("INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a')");
        sql("INSERT INTO " + schema + ".tenant_memberships VALUES('tenant-a','member-a',true)");
        sql("INSERT INTO " + schema + ".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle)"
                + " VALUES('item','res-a','tenant-a','member-a','ACTIVE')");
        for (String operation : List.of("create","read","activate","transition"))
            sql("INSERT INTO " + schema + ".resource_acl VALUES('tenant-a','item','res-a','member-a','workflow:" + operation + "',true)");
        sql("INSERT INTO " + schema + ".workflow_definitions(definition_id,definition_version,initial_state) VALUES('publication',7,'draft')");
        sql("INSERT INTO " + schema + ".workflow_definition_states VALUES('publication',7,'draft'),('publication',7,'done')");
        sql("INSERT INTO " + schema + ".workflow_definition_transitions VALUES('publication',7,'finish','draft','done')");
        sql("INSERT INTO " + schema + ".workflow_definition_bindings VALUES('tenant-a','item','publication',7,true)");
        sql("CREATE ROLE " + runtimeRole + " LOGIN PASSWORD '" + runtimePassword
                + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT");
        sql("REVOKE ALL ON SCHEMA " + schema + " FROM PUBLIC");
        sql("REVOKE ALL ON ALL FUNCTIONS IN SCHEMA " + schema + " FROM PUBLIC");
        List<String> functions = new ArrayList<>();
        List<String> ports = new ArrayList<>();
        try (var statement = administrator.createStatement(); var rows = statement.executeQuery(
                "SELECT quote_ident(p.proname)||'('||pg_get_function_identity_arguments(p.oid)||')',p.proname "
                + "FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='" + schema + "'")) {
            while (rows.next()) {
                functions.add(rows.getString(1));
                if (rows.getString(2).startsWith("wf_port_")) ports.add(rows.getString(1));
            }
        }
        assertThat(ports).hasSize(6);
        // Flush the V3 deferred initial-state FK before changing fixture ownership.
        sql("SET CONSTRAINTS ALL IMMEDIATE");
        for (String table : List.of("tenant_guards","tenant_memberships","resource_registry","resource_acl",
                "workflow_definition_bindings","workflow_policy_audit","workflow_definitions","workflow_definition_states",
                "workflow_definition_transitions","workflow_instances","workflow_transition_history"))
            sql("ALTER TABLE " + schema + "." + table + " OWNER TO " + ownerRole);
        for (String function : functions) sql("ALTER FUNCTION " + schema + "." + function + " OWNER TO " + ownerRole);
        sql("ALTER SCHEMA " + schema + " OWNER TO " + ownerRole);
        sql("GRANT USAGE ON SCHEMA " + schema + " TO " + runtimeRole);
        for (String port : ports) sql("GRANT EXECUTE ON FUNCTION " + schema + "." + port + " TO " + runtimeRole);
        administrator.commit();
        database = new DriverManagerDataSource(jdbcUrl, runtimeRole, runtimePassword);
        Properties properties = new Properties();
        properties.setProperty("currentSchema",schema);
        database.setConnectionProperties(properties);
    }

    @AfterEach void cleanup() throws Exception {
        if (administrator == null) return;
        try {
            administrator.rollback();
            if (schema != null && schema.matches("s6_login_[a-f0-9]{32}")) sql("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            if (runtimeRole != null && runtimeRole.matches("s6_rt_[a-f0-9]{32}")) sql("DROP ROLE IF EXISTS " + runtimeRole);
            if (ownerRole != null && ownerRole.matches("s6_owner_[a-f0-9]{32}")) sql("DROP ROLE IF EXISTS " + ownerRole);
            administrator.commit();
        } finally { administrator.close(); }
    }

    @Test void actualLoginHasExactlySixReviewedSignaturesAndNoOwnerPublicOrInheritedBypass() throws Exception {
        try (Connection runtime = database.getConnection(); var statement = runtime.createStatement()) {
            assertThat(text(runtime,"SELECT session_user")).isEqualTo(runtimeRole);
            assertThat(number("SELECT (rolcanlogin OR rolsuper OR rolcreaterole OR rolbypassrls)::int FROM pg_roles WHERE rolname='" + ownerRole + "'")).isZero();
            assertThat(text(runtime,"SELECT pg_has_role(current_user,'" + ownerRole + "','MEMBER')::text")).isEqualTo("false");
            assertThat(number("SELECT count(*) FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname='" + runtimeRole + "')")).isZero();
            int count = 0;
            try (var rows = statement.executeQuery("SELECT p.proname,p.prosecdef,r.rolname,p.proconfig::text,"
                    + "has_function_privilege(current_user,p.oid,'EXECUTE'),"
                    + "EXISTS(SELECT 1 FROM aclexplode(COALESCE(p.proacl,acldefault('f',p.proowner))) a WHERE a.grantee=0 AND a.privilege_type='EXECUTE') "
                    + "FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace JOIN pg_roles r ON r.oid=p.proowner WHERE n.nspname='" + schema + "'")) {
                while(rows.next()) {
                    boolean port = rows.getString(1).startsWith("wf_port_");
                    assertThat(rows.getBoolean(5)).isEqualTo(port);
                    assertThat(rows.getBoolean(6)).isFalse();
                    if (port) {
                        count++;
                        assertThat(rows.getBoolean(2)).isTrue();
                        assertThat(rows.getString(3)).isEqualTo(ownerRole);
                        assertThat(rows.getString(4)).contains("search_path=pg_catalog, pg_temp");
                    }
                }
            }
            assertThat(count).isEqualTo(6);
            assertThat(number("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='" + schema
                    + "' AND p.proname='wf_port_commit_activation' AND oidvectortypes(p.proargtypes)='text, boolean, jsonb, jsonb'")).isEqualTo(1);
            assertThat(number("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='" + schema
                    + "' AND p.proname='wf_port_commit_transition' AND oidvectortypes(p.proargtypes)='text, boolean, jsonb, jsonb, jsonb'")).isEqualTo(1);
        }
    }

    @Test void independentLoginCannotReadWriteOwnOrEscalateProtectedDatabase() throws Exception {
        try (Connection runtime = database.getConnection()) {
            runtime.setAutoCommit(false);
            for (String table : List.of("tenant_guards","tenant_memberships","resource_registry","resource_acl","workflow_definition_bindings",
                    "workflow_policy_audit","workflow_definitions","workflow_definition_states","workflow_definition_transitions","workflow_instances","workflow_transition_history")) {
                rejected(runtime,"SELECT * FROM " + schema + "." + table);
                rejected(runtime,"TRUNCATE " + schema + "." + table);
            }
            for(String query : List.of("UPDATE " + schema + ".resource_acl SET granted=false",
                    "INSERT INTO " + schema + ".workflow_instances(instance_id) VALUES('forged')",
                    "DELETE FROM " + schema + ".tenant_memberships", "CREATE TABLE " + schema + ".unapproved(id int)",
                    "SET ROLE " + ownerRole,"SET ROLE stage6_test",
                    "SELECT " + schema + ".stage6_policy_immutability_guard()")) rejected(runtime,query);
        }
    }

    @Test void directDefinitionProtocolUsesTheSameReviewedLoginAndGuard() throws Exception {
        assertThatCode(()->new PostgresWorkflowPersistence(database,schema).guardedTransaction(false,connection->true))
                .as("guarded JDBC transaction").doesNotThrowAnyException();
        try(Connection runtime=database.getConnection()) {
            WorkflowEventTriggerGuard.REVIEWED.verify(runtime,schema);
            for(String phase:List.of("CLASSIFY","MATERIALIZE")) {
                try(var statement=runtime.prepareStatement("SELECT outcome,body FROM "+schema
                        +".wf_port_load_definition(?,?,?,?,?,?,?,?,?)")) {
                    statement.setString(1,phase); statement.setString(2,"member-a"); statement.setString(3,"CREATE");
                    statement.setBoolean(4,true); statement.setString(5,INSTANCE_ID); statement.setString(6,"item");
                    statement.setString(7,"res-a"); statement.setString(8,"publication"); statement.setLong(9,7);
                    try(var rows=statement.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo(phase.equals("CLASSIFY")?"FACTS":"DEFINITION");
                        assertThat(rows.getString(2)).isNotNull();
                    }
                }
            }
        }
        var port=new PostgresWorkflowPersistence(database,schema).forStage6(principal(),WorkflowOperation.CREATE,INSTANCE_ID,RESOURCE,"publication",7L,null,null);
        assertThatCode(()->port.loadDefinition("publication",7)).as("definition decoding").doesNotThrowAnyException();
        try(Connection runtime=database.getConnection();var statement=runtime.prepareStatement(
                "SELECT outcome,body FROM "+schema+".wf_port_create_instance(?,?,?::jsonb)")) {
            var initial=Stage6WorkflowPersistence.snapshot(WorkflowInstance.notStarted("wf_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",definition(),RESOURCE,CREATED));
            statement.setString(1,"member-a"); statement.setBoolean(2,true); statement.setString(3,initial.toString());
            try(var rows=statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).as("raw CREATE outcome").isEqualTo("SNAPSHOT");
                assertThat(new com.fasterxml.jackson.databind.ObjectMapper()
                        .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_LONG_FOR_INTS).readTree(rows.getString(2)))
                        .as("canonical CREATE response").isEqualTo(initial);
            }
        }
        assertThatCode(()->port.createInstance(WorkflowInstance.notStarted(INSTANCE_ID,definition(),RESOURCE,CREATED)))
                .as("CREATE round trip").doesNotThrowAnyException();
    }

    @Test void allSixPortsPreserveCoreSnapshotsHistoryAndExactInstants() {
        WorkflowInstance initial=create(INSTANCE_ID);
        assertThat(initial.createdAt()).isEqualTo(CREATED);
        WorkflowInstance active=activate(INSTANCE_ID);
        assertThat(active.runtimeVersion()).isZero();
        assertThat(read(INSTANCE_ID).loadHistory(INSTANCE_ID)).isEmpty();
        WorkflowPersistencePort transition=port(database,WorkflowOperation.TRANSITION,INSTANCE_ID,"finish",0L);
        TransitionResult result=new WorkflowRuntime(transition,Clock.fixed(FINISHED,ZoneOffset.UTC))
                .executeTransition(new TransitionCommand(INSTANCE_ID,"finish",new ActorReference("member-a"),0));
        assertThat(result.instance().lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
        assertThat(result.instance().updatedAt()).isEqualTo(FINISHED);
        assertThat(read(INSTANCE_ID).loadHistory(INSTANCE_ID)).containsExactly(result.record());
        PostgresWorkflowPersistence.requireSnapshot(result.instance(),read(INSTANCE_ID).loadInstance(INSTANCE_ID));
    }

    @Test void replayActivationConflictsEvenWhileVersionRemainsZero() {
        WorkflowInstance initial=create(INSTANCE_ID);
        WorkflowPersistencePort port=port(database,WorkflowOperation.ACTIVATE,INSTANCE_ID,null,null);
        WorkflowInstance active=initial.activate(definition(),ACTIVATED);
        port.commitActivation(initial,active);
        code(WorkflowErrorCode.VERSION_CONFLICT,()->port.commitActivation(initial,active));
        code(WorkflowErrorCode.TRANSITION_NOT_ALLOWED,()->port.commitActivation(active,active));
        assertThat(read(INSTANCE_ID).loadHistory(INSTANCE_ID)).isEmpty();
    }

    @Test void duplicateCreateUsesCoreErrorWithoutCollisionLookupOrOverwrite() {
        WorkflowInstance initial=create(INSTANCE_ID);
        code(WorkflowErrorCode.INSTANCE_ALREADY_EXISTS,()->create(INSTANCE_ID));
        PostgresWorkflowPersistence.requireSnapshot(initial,read(INSTANCE_ID).loadInstance(INSTANCE_ID));
        assertThat(read(INSTANCE_ID).loadHistory(INSTANCE_ID)).isEmpty();
    }

    @Test void internalActionsNeedOnlyTheirOwnAuthorityAndGrant() throws Exception {
        create(INSTANCE_ID);
        sql("SELECT tenant_id FROM "+schema+".tenant_guards WHERE tenant_id='tenant-a' FOR UPDATE");
        sql("UPDATE "+schema+".resource_acl SET granted=false WHERE operation='workflow:read'");
        administrator.commit();
        CurrentPrincipal verified=mock(CurrentPrincipal.class);
        when(verified.requireVerified()).thenReturn(new AuthenticatedPrincipal("member-a","Member A",Set.of(new Authority("workflow:activate")),true));
        var activation=new PostgresWorkflowPersistence(database,schema).forStage6(verified,WorkflowOperation.ACTIVATE,INSTANCE_ID,null,null,null,null,null);
        var active=new WorkflowRuntime(activation,Clock.fixed(ACTIVATED,ZoneOffset.UTC)).activateInstance(INSTANCE_ID);
        when(verified.requireVerified()).thenReturn(new AuthenticatedPrincipal("member-a","Member A",Set.of(new Authority("workflow:transition")),true));
        var transition=new PostgresWorkflowPersistence(database,schema).forStage6(verified,WorkflowOperation.TRANSITION,INSTANCE_ID,null,null,null,"finish",0L);
        var result=transition.commitTransition(active.transition(definition(),new TransitionCommand(INSTANCE_ID,"finish",new ActorReference("member-a"),0),FINISHED));
        assertThat(result.instance().lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
        denied(WorkflowAccessDecision.FORBIDDEN,()->read(INSTANCE_ID).loadHistory(INSTANCE_ID));
    }

    @Test void transitionRejectsForgedSamePinGraphActorAndPreviousSnapshotWithoutWriting() {
        create(INSTANCE_ID);
        WorkflowInstance active=activate(INSTANCE_ID);
        var transition=port(database,WorkflowOperation.TRANSITION,INSTANCE_ID,"finish",0L);
        var forgedDefinition=new WorkflowDefinition("publication",7,"draft",Set.of("draft","done"),
                Set.of(new WorkflowTransition("finish","draft","draft")));
        var fake=WorkflowInstance.restore(INSTANCE_ID,forgedDefinition,RESOURCE,"draft",0,WorkflowLifecycle.ACTIVE,CREATED,ACTIVATED);
        code(WorkflowErrorCode.TRANSITION_NOT_ALLOWED,()->transition.commitTransition(fake.transition(forgedDefinition,
                new TransitionCommand(INSTANCE_ID,"finish",new ActorReference("member-a"),0),FINISHED)));
        denied(WorkflowAccessDecision.UNAVAILABLE,()->transition.commitTransition(active.transition(definition(),
                new TransitionCommand(INSTANCE_ID,"finish",new ActorReference("forged-actor"),0),FINISHED)));
        var wrongOrigin=WorkflowInstance.restore(INSTANCE_ID,definition(),new ResourceReference("item","other"),"draft",0,
                WorkflowLifecycle.ACTIVE,CREATED,ACTIVATED);
        code(WorkflowErrorCode.TRANSITION_NOT_ALLOWED,()->transition.commitTransition(wrongOrigin.transition(definition(),
                new TransitionCommand(INSTANCE_ID,"finish",new ActorReference("member-a"),0),FINISHED)));
        PostgresWorkflowPersistence.requireSnapshot(active,read(INSTANCE_ID).loadInstance(INSTANCE_ID));
        assertThat(read(INSTANCE_ID).loadHistory(INSTANCE_ID)).isEmpty();
    }

    static java.util.stream.Stream<Arguments> snapshotFields() {
        return java.util.stream.Stream.of("activate","transition").flatMap(operation -> java.util.stream.Stream.of("definition_id",
                "definition_version","resource_type","resource_id","current_state","created_at_seconds","created_at_nanos",
                "updated_at_seconds","updated_at_nanos").map(field -> Arguments.of(operation,field)));
    }

    @ParameterizedTest @MethodSource("snapshotFields")
    void finalSqlComparesEveryExpectedSnapshotField(String operation,String field) throws Exception {
        WorkflowInstance initial=create(INSTANCE_ID);
        boolean activation=operation.equals("activate");
        WorkflowInstance previous=activation?initial:activate(INSTANCE_ID);
        var transition=activation?null:previous.transition(definition(),new TransitionCommand(INSTANCE_ID,"finish",new ActorReference("member-a"),0),FINISHED);
        ObjectNode expected=Stage6WorkflowPersistence.snapshot(previous);
        if(expected.get(field).isTextual()) expected.put(field,"tampered");
        else expected.put(field,expected.get(field).longValue()+1);
        try(Connection runtime=database.getConnection(); var statement=runtime.prepareStatement(
                "SELECT outcome,body->>'code' FROM " + schema + (activation?".wf_port_commit_activation(?,?,?::jsonb,?::jsonb)"
                        :".wf_port_commit_transition(?,?,?::jsonb,?::jsonb,?::jsonb)"))) {
            statement.setString(1,"member-a"); statement.setBoolean(2,true);
            statement.setString(3,expected.toString());
            statement.setString(4,Stage6WorkflowPersistence.snapshot(activation?initial.activate(definition(),ACTIVATED):transition.instance()).toString());
            if(!activation) statement.setString(5,Stage6WorkflowPersistence.record(transition.record()).toString());
            try(var rows=statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("DOMAIN_ERROR");
                assertThat(rows.getString(2)).isEqualTo("TRANSITION_NOT_ALLOWED");
            }
        }
        PostgresWorkflowPersistence.requireSnapshot(previous,read(INSTANCE_ID).loadInstance(INSTANCE_ID));
    }

    @Test void finalAclRecheckRejectsRevocationBetweenProposalAndCommit() throws Exception {
        create(INSTANCE_ID);
        WorkflowInstance active=activate(INSTANCE_ID);
        var proposal=active.transition(definition(),new TransitionCommand(INSTANCE_ID,"finish",new ActorReference("member-a"),0),FINISHED);
        AtomicInteger attempts=new AtomicInteger();
        var guarded=port(faultSource("revoke",attempts),WorkflowOperation.TRANSITION,INSTANCE_ID,"finish",0L);
        denied(WorkflowAccessDecision.FORBIDDEN,()->guarded.commitTransition(proposal));
        assertThat(attempts).hasValue(1);
        assertThat(read(INSTANCE_ID).loadHistory(INSTANCE_ID)).isEmpty();
    }

    static java.util.stream.Stream<Arguments> writeFaults() {
        return java.util.stream.Stream.of("create","activate","transition").flatMap(operation ->
                java.util.stream.Stream.of("statement","rollback","commit-before","commit-after").map(fault -> Arguments.of(operation,fault)));
    }

    @ParameterizedTest @MethodSource("writeFaults")
    void guardedAdapterFaultsKeepAtomicOutcomeClassificationAndNeverRetry(String operation,String fault) throws Exception {
        WorkflowInstance initial=operation.equals("create")
                ? WorkflowInstance.notStarted(INSTANCE_ID,definition(),RESOURCE,CREATED) : create(INSTANCE_ID);
        WorkflowInstance active=operation.equals("transition")?activate(INSTANCE_ID):initial.activate(definition(),ACTIVATED);
        TransitionResult proposal=active.transition(definition(),new TransitionCommand(INSTANCE_ID,"finish",new ActorReference("member-a"),0),FINISHED);
        AtomicInteger attempts=new AtomicInteger();
        DataSource faulty=faultSource(fault,attempts);
        Runnable write=switch(operation) {
            case "create" -> () -> new PostgresWorkflowPersistence(faulty,schema)
                    .forStage6(principal(),WorkflowOperation.CREATE,INSTANCE_ID,RESOURCE,"publication",7L,null,null).createInstance(initial);
            case "activate" -> () -> port(faulty,WorkflowOperation.ACTIVATE,INSTANCE_ID,null,null).commitActivation(initial,active);
            default -> () -> port(faulty,WorkflowOperation.TRANSITION,INSTANCE_ID,"finish",0L).commitTransition(proposal);
        };
        if(fault.equals("statement")) denied(WorkflowAccessDecision.UNAVAILABLE,write);
        else code(WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN,write);
        assertThat(attempts).hasValue(1);
        if(fault.equals("commit-after")) {
            WorkflowInstance committed=switch(operation) { case "create" -> initial; case "activate" -> active; default -> proposal.instance(); };
            PostgresWorkflowPersistence.requireSnapshot(committed,read(INSTANCE_ID).loadInstance(INSTANCE_ID));
            assertThat(read(INSTANCE_ID).loadHistory(INSTANCE_ID)).containsExactlyElementsOf(operation.equals("transition")?List.of(proposal.record()):List.of());
            if(operation.equals("transition"))
                code(WorkflowErrorCode.VERSION_CONFLICT,()->port(database,WorkflowOperation.TRANSITION,INSTANCE_ID,"finish",0L).commitTransition(proposal));
        } else if(operation.equals("create")) {
            denied(WorkflowAccessDecision.HIDDEN,()->read(INSTANCE_ID).loadInstance(INSTANCE_ID));
            assertThat(number("SELECT count(*) FROM "+schema+".workflow_instances")).isZero();
            assertThat(number("SELECT count(*) FROM "+schema+".workflow_transition_history")).isZero();
        } else {
            PostgresWorkflowPersistence.requireSnapshot(operation.equals("activate")?initial:active,read(INSTANCE_ID).loadInstance(INSTANCE_ID));
            assertThat(read(INSTANCE_ID).loadHistory(INSTANCE_ID)).isEmpty();
        }
    }

    private DataSource faultSource(String fault,AtomicInteger attempts) {
        // Same JDBC proxy fault points as PostgresWorkflowPersistenceIntegrationTest; only the final guarded write is faulted.
        return new DelegatingDataSource(database) {
            @Override public Connection getConnection() throws SQLException {
                Connection real=super.getConnection();
                boolean[] writing={false};
                return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                    try {
                        if(method.getName().equals("prepareStatement") && (((String)args[0]).contains(".wf_port_commit_transition(")
                                || ((String)args[0]).contains(".wf_port_commit_activation(") || ((String)args[0]).contains(".wf_port_create_instance("))) {
                            writing[0]=true;
                            if(fault.equals("revoke")) {
                                // All preliminary Core reads have ended; revoke immediately before the final SQL call.
                                sql("SELECT tenant_id FROM "+schema+".tenant_guards WHERE tenant_id='tenant-a' FOR UPDATE");
                                sql("UPDATE "+schema+".resource_acl SET granted=false WHERE operation='workflow:transition'");
                                administrator.commit();
                            }
                            PreparedStatement statement=(PreparedStatement)method.invoke(real,args);
                            return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                                try {
                                    if(m.getName().equals("executeQuery")) {
                                        attempts.incrementAndGet();
                                        Object result=m.invoke(statement,a);
                                        if(fault.equals("statement")||fault.equals("rollback")) {
                                            ((ResultSet)result).close();
                                            throw new SQLException("injected statement acknowledgement loss","08006");
                                        }
                                        return result;
                                    }
                                    return m.invoke(statement,a);
                                } catch(InvocationTargetException failure) { throw failure.getCause(); }
                            });
                        }
                        if(writing[0] && method.getName().equals("commit") && fault.startsWith("commit-")) {
                            if(fault.equals("commit-after")) real.commit();
                            throw new SQLException("injected commit acknowledgement loss","08006");
                        }
                        if(writing[0] && method.getName().equals("rollback") && fault.equals("rollback"))
                            throw new SQLException("injected rollback failure","08006");
                        return method.invoke(real,args);
                    } catch(InvocationTargetException failure) { throw failure.getCause(); }
                });
            }
        };
    }

    private WorkflowInstance create(String id) {
        var port=new PostgresWorkflowPersistence(database,schema).forStage6(principal(),WorkflowOperation.CREATE,id,RESOURCE,"publication",7L,null,null);
        return port.createInstance(WorkflowInstance.notStarted(id,port.loadDefinition("publication",7),RESOURCE,CREATED));
    }
    private WorkflowInstance activate(String id) {
        return new WorkflowRuntime(port(database,WorkflowOperation.ACTIVATE,id,null,null),Clock.fixed(ACTIVATED,ZoneOffset.UTC)).activateInstance(id);
    }
    private WorkflowPersistencePort read(String id) { return port(database,WorkflowOperation.READ,id,null,null); }
    private WorkflowPersistencePort port(DataSource source,WorkflowOperation operation,String id,String transition,Long version) {
        return new PostgresWorkflowPersistence(source,schema).forStage6(principal(),operation,id,null,null,null,transition,version);
    }
    private CurrentPrincipal principal() {
        CurrentPrincipal principal=mock(CurrentPrincipal.class);
        when(principal.requireVerified()).thenReturn(new AuthenticatedPrincipal("member-a","Member A",Set.of(new Authority("workflow:create"),
                new Authority("workflow:read"),new Authority("workflow:activate"),new Authority("workflow:transition")),true));
        return principal;
    }
    private WorkflowDefinition definition() {
        return new WorkflowDefinition("publication",7,"draft",Set.of("draft","done"),Set.of(new WorkflowTransition("finish","draft","done")));
    }
    private void code(WorkflowErrorCode code,Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(WorkflowException.class,e->assertThat(e.code()).isEqualTo(code));
    }
    private void denied(WorkflowAccessDecision decision,Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(WorkflowAccessException.class,e->assertThat(e.decision()).isEqualTo(decision)).hasNoCause();
    }
    private void rejected(Connection connection,String sql) throws Exception {
        Savepoint save=connection.setSavepoint();
        try { assertThatThrownBy(()->execute(connection,sql)).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("42501")); }
        finally { connection.rollback(save); connection.releaseSavepoint(save); }
    }
    private int number(String query) throws SQLException { return Integer.parseInt(text(query)); }
    private String text(String query) throws SQLException { return text(administrator,query); }
    private String text(Connection connection,String query) throws SQLException {
        try(var statement=connection.createStatement();var rows=statement.executeQuery(query)) { assertThat(rows.next()).isTrue(); return rows.getString(1); }
    }
    private void sql(String sql) throws SQLException { execute(administrator,sql); }
    private void execute(Connection connection,String sql) throws SQLException { try(var statement=connection.createStatement()) { statement.execute(sql); } }
}
