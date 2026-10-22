package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Three real LOGIN classes, six D3 ports and two bounded offline mutators; disposable 55427 only. */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):55427/stage6_disposable_test")
class Stage6RuntimeLoginPrivilegeIntegrationTest {
    private static final String INSTANCE_ID = "wf_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private Connection administrator;
    private String schema, runtimeRole, ownerRole, runtimePassword, jdbcUrl;
    private DriverManagerDataSource database;
    private String provisionerRole, outsiderRole, provisionerPassword, outsiderPassword;
    private ObjectNode membershipApproval, aclApproval;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MEMBERSHIP = "stage6_policy_membership_candidate(text,text,boolean,text,bigint,uuid,text,text)";
    private static final String ACL = "stage6_policy_acl_candidate(text,text,text,text,text,boolean,bigint,uuid,text,text)";
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
        provisionerRole = "s6_prov_" + suffix;
        outsiderRole = "s6_other_" + suffix;
        byte[] entropy = new byte[24];
        new SecureRandom().nextBytes(entropy);
        runtimePassword = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        new SecureRandom().nextBytes(entropy);
        provisionerPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        new SecureRandom().nextBytes(entropy);
        outsiderPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        sql("CREATE ROLE " + ownerRole + " NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT");
        sql("CREATE SCHEMA " + schema);
        for (String file : List.of("src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                "src/test/resources/stage6/Stage6_policy_lifecycle_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_read_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_create_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_actions_candidate.sql")) {
            sql(Files.readString(Path.of(file)).replace("${flyway:defaultSchema}", schema));
        }
        sql("INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a')");
        sql("INSERT INTO " + schema + ".tenant_memberships VALUES('tenant-a','member-a',true)");
        sql("INSERT INTO " + schema + ".tenant_memberships VALUES('tenant-a','member-b',true)");
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
        sql("CREATE ROLE " + provisionerRole + " LOGIN PASSWORD '" + provisionerPassword
                + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT");
        sql("CREATE ROLE " + outsiderRole + " LOGIN PASSWORD '" + outsiderPassword
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
        sql("ALTER DEFAULT PRIVILEGES FOR ROLE " + ownerRole + " REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC");
        sql("GRANT USAGE ON SCHEMA " + schema + " TO " + provisionerRole + "," + outsiderRole);
        membershipApproval = approval("membership");
        membershipApproval.put("active",false).put("replacement","member-b");
        aclApproval = approval("acl");
        aclApproval.put("type","item").put("resource","res-a").put("operation","workflow:read").put("granted",false);
        installApproval(MEMBERSHIP,membershipApproval);
        installApproval(ACL,aclApproval);
        sql("ALTER FUNCTION " + schema + ".stage6_policy_lifecycle_check() SECURITY DEFINER");
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
            if (ownerRole != null && ownerRole.matches("s6_owner_[a-f0-9]{32}")) {
                sql("DROP OWNED BY " + ownerRole);
                sql("DROP ROLE IF EXISTS " + ownerRole);
            }
            if (provisionerRole != null && provisionerRole.matches("s6_prov_[a-f0-9]{32}")) sql("DROP ROLE IF EXISTS " + provisionerRole);
            if (outsiderRole != null && outsiderRole.matches("s6_other_[a-f0-9]{32}")) sql("DROP ROLE IF EXISTS " + outsiderRole);
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

    @ParameterizedTest @ValueSource(booleans={false,true})
    void runtimeAndOutsiderPasswordsAreNotInterchangeable(boolean outsiderToRuntime) throws Exception {
        assertThat(outsiderPassword.equals(runtimePassword) || outsiderPassword.equals(provisionerPassword)).isFalse();
        String role=outsiderToRuntime?runtimeRole:outsiderRole;
        String wrongPassword=outsiderToRuntime?outsiderPassword:runtimePassword;
        try(Connection correct=login(role)) { assertThat(text(correct,"SELECT session_user")).isEqualTo(role); }
        assertThatThrownBy(()->{
            try(Connection ignored=login(role,wrongPassword)) { }
        }).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("28P01"));
    }

    @Test void allThreeLoginsHaveOnlyTheirExactFunctionAllowlistAndNoEscalation() throws Exception {
        assertThat(text("SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname='"+schema+"'")).isEqualTo(ownerRole);
        assertThat(number("SELECT count(*) FROM pg_class WHERE relnamespace='"+schema+"'::regnamespace AND relkind='r' AND relowner<>'"+ownerRole+"'::regrole")).isZero();
        for (String role : List.of(runtimeRole,provisionerRole,outsiderRole)) {
            try (Connection login = login(role)) {
                assertThat(text(login,"SELECT session_user")).isEqualTo(role);
                assertThat(number("SELECT count(*) FROM pg_auth_members WHERE member='"+role+"'::regrole")).isZero();
                assertThat(number("SELECT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR rolinherit)::int FROM pg_roles WHERE rolname='"+role+"'")).isZero();
                try (var s=login.createStatement(); var rows=s.executeQuery("SELECT p.proname,has_function_privilege(current_user,p.oid,'EXECUTE'),"
                        +"p.prosecdef,pg_get_userbyid(p.proowner),p.proconfig::text FROM pg_proc p WHERE p.pronamespace='"+schema+"'::regnamespace")) {
                    int allowed=0;
                    while(rows.next()) {
                        String name=rows.getString(1);
                        boolean expected=role.equals(runtimeRole)?name.startsWith("wf_port_")
                                :role.equals(provisionerRole)&&(name.equals("stage6_policy_membership_candidate")||name.equals("stage6_policy_acl_candidate"));
                        assertThat(rows.getBoolean(2)).as(role+"/"+name).isEqualTo(expected);
                        if(expected) {
                            allowed++;
                            assertThat(rows.getBoolean(3)).isTrue();
                            assertThat(rows.getString(4)).isEqualTo(ownerRole);
                            assertThat(rows.getString(5)).contains("search_path=pg_catalog, pg_temp");
                        }
                    }
                    assertThat(allowed).isEqualTo(role.equals(runtimeRole)?6:role.equals(provisionerRole)?2:0);
                }
                for(String target:List.of(ownerRole,"stage6_test",runtimeRole,provisionerRole,outsiderRole))
                    if(!target.equals(role)) rejected(login,"SET ROLE "+target);
                for(String command:List.of("SET SESSION AUTHORIZATION stage6_test","SET session_replication_role=replica",
                        "ALTER ROLE "+role+" SUPERUSER","CREATE TABLE "+schema+".forged(id int)",
                        "ALTER FUNCTION "+schema+"."+ACL+" SECURITY INVOKER",
                        "SELECT "+schema+".stage6_policy_lifecycle_check()",
                        "SELECT "+schema+".stage6_policy_immutability_guard()")) rejected(login,command);
                try(var s=administrator.createStatement();var rows=s.executeQuery("SELECT c.relname,a.attname FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=1 WHERE c.relnamespace='"+schema+"'::regnamespace AND c.relkind='r'")) {
                    while(rows.next()) {
                        String table=schema+"."+rows.getString(1),column=rows.getString(2);
                        for(String command:List.of("SELECT * FROM "+table,"INSERT INTO "+table+" DEFAULT VALUES",
                                "UPDATE "+table+" SET "+column+"="+column,"DELETE FROM "+table,"TRUNCATE "+table,
                                "ALTER TABLE "+table+" ADD COLUMN forged int")) rejected(login,command);
                    }
                }
                if(!role.equals(provisionerRole)) {
                    rejected(login,policySql(true,membershipApproval));
                    rejected(login,policySql(false,aclApproval));
                } else rejected(login,"SELECT * FROM "+schema+".wf_port_load_instance('member-a','READ',true,'"+INSTANCE_ID+"')");
            }
        }
        assertThat(number("SELECT count(*) FROM pg_proc p CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a WHERE p.pronamespace='"+schema+"'::regnamespace AND a.grantee=0 AND a.privilege_type='EXECUTE'")).isZero();
        // A newly created owner function also has no default PUBLIC EXECUTE.
        sql("SET LOCAL ROLE "+ownerRole);
        sql("CREATE FUNCTION "+schema+".default_privilege_probe() RETURNS int LANGUAGE sql AS 'SELECT 1'");
        sql("RESET ROLE");
        assertThat(number("SELECT count(*) FROM pg_proc p CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a WHERE p.oid='"+schema+".default_privilege_probe()'::regprocedure AND a.grantee=0")).isZero();
        administrator.rollback();
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void offlineMutationCommitsExactLoginManifestRevisionAndAudit(boolean membership) throws Exception {
        ObjectNode approval=membership?membershipApproval:aclApproval;
        int writer;
        try(Connection offline=login(provisionerRole)) {
            writer=Integer.parseInt(text(offline,"SELECT pg_backend_pid()"));
            assertThat(text(offline,policySql(membership,approval))).isEqualTo("CHANGED");
            offline.commit();
        }
        try(Connection observer=login("stage6_test")) {
            assertThat(text(observer,"SELECT pg_backend_pid()")).isNotEqualTo(Integer.toString(writer));
            assertThat(text(observer,"SELECT acl_revision FROM "+schema+".tenant_guards")).isEqualTo("1");
            var event=JSON.readTree(text(observer,"SELECT to_jsonb(a)::text FROM "+schema+".workflow_policy_audit a"));
            assertThat(event.get("actor_id").asText()).isEqualTo(provisionerRole);
            assertThat(event.get("actor_source_ref").asText()).isEqualTo("test-only/synthetic-operator");
            assertThat(event.get("audit_event_id").asText()).isEqualTo(approval.get("event").asText());
            assertThat(event.get("manifest_digest").asText()).isEqualTo(approval.get("manifest_digest").asText());
            assertThat(event.get("previous_acl_revision").asLong()).isZero();
            assertThat(event.get("resulting_acl_revision").asLong()).isEqualTo(1);
            assertThat(event.get("change_set").size()).isEqualTo(membership?6:1);
        }
    }

    @Test void publicFunctionCatalogDoesNotExposeApprovedTenantResourceMemberOrEvent() throws Exception {
        try(Connection unrelated=login(outsiderRole);var statement=unrelated.createStatement();var rows=statement.executeQuery(
                "SELECT substr(config,length('stage6.offline_approval=')+1) FROM pg_proc p CROSS JOIN LATERAL unnest(p.proconfig) config "
                        +"WHERE p.pronamespace='"+schema+"'::regnamespace AND config LIKE 'stage6.offline_approval=%'")) {
            int count=0;
            while(rows.next()) {
                Set<String> keys=new HashSet<>(); JSON.readTree(rows.getString(1)).fieldNames().forEachRemaining(keys::add);
                assertThat(keys).containsExactlyInAnyOrder("operator_ref","fixture_digest","not_before","expires_at","manifest_digest");
                count++;
            }
            assertThat(count).isEqualTo(2);
        }
    }

    @ParameterizedTest @ValueSource(strings={"login","database","schema","action","tenant","member","revision","event","reason","correlation","operator_ref","fixture_digest","manifest_digest","expires_at","not_before","resource","operation","granted","active","replacement","missing"})
    void missingWrongExpiredOrStaleApprovalFailsClosed(String field) throws Exception {
        for(boolean membership:List.of(false,true)) {
            ObjectNode original=membership?membershipApproval:aclApproval;
            if(!original.has(field)&&!field.equals("missing")) continue;
            ObjectNode invalid=original.deepCopy();
            if(field.equals("missing")) invalid.removeAll();
            else if(field.equals("expires_at")) invalid.put(field,Instant.now().minusSeconds(60).toString());
            else if(field.equals("not_before")) invalid.put(field,Instant.now().plusSeconds(600).toString());
            else if(field.equals("revision")) invalid.put(field,7);
            else if(field.equals("granted")||field.equals("active")) invalid.put(field,!invalid.get(field).asBoolean());
            else invalid.put(field,"wrong");
            if(!field.equals("manifest_digest")) invalid.remove("manifest_digest");
            installApproval(membership?MEMBERSHIP:ACL,invalid);
            administrator.commit();
            try(Connection offline=login(provisionerRole)) { rejected(offline,policySql(membership,original)); }
        }
        assertPolicyState(false);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void callerSettingsCannotForgeMissingInstalledApproval(boolean membership) throws Exception {
        String signature=membership?MEMBERSHIP:ACL;
        ObjectNode approved=membership?membershipApproval:aclApproval;
        sql("ALTER FUNCTION "+schema+"."+signature+" RESET stage6.offline_approval");
        administrator.commit();
        try(Connection offline=login(provisionerRole)) {
            execute(offline,"SET stage6.offline_approval="+literal(approved.toString()));
            rejected(offline,policySql(membership,approved));
        }
        assertPolicyState(false);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void quotedSqlPayloadCannotChangeAnApprovedTarget(boolean membership) throws Exception {
        ObjectNode input=(membership?membershipApproval:aclApproval).deepCopy();
        input.put("member","member-a'; SELECT pg_catalog.set_config('role','stage6_test',false); --");
        try(Connection offline=login(provisionerRole)) { rejected(offline,policySql(membership,input)); }
        assertPolicyState(false);
    }

    @ParameterizedTest @ValueSource(strings={"REPEATABLE READ","SERIALIZABLE"})
    void offlineMutationRejectsIsolationThatCouldKeepAnOldApprovalSnapshot(String isolation) throws Exception {
        for(boolean membership:List.of(false,true)) {
            try(Connection offline=login(provisionerRole)) {
                offline.setTransactionIsolation(isolation.equals("REPEATABLE READ")
                        ?Connection.TRANSACTION_REPEATABLE_READ:Connection.TRANSACTION_SERIALIZABLE);
                assertThatThrownBy(()->text(offline,policySql(membership,membership?membershipApproval:aclApproval)))
                        .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("25001"));
                offline.rollback();
            }
        }
        assertPolicyState(false);
    }

    @Test void callerSearchPathAndTempTablesCannotRedirectOfflineMutation() throws Exception {
        try(Connection offline=login(provisionerRole)) {
            execute(offline,"CREATE TEMP TABLE resource_acl(granted boolean)");
            execute(offline,"INSERT INTO resource_acl VALUES(true)");
            execute(offline,"SET LOCAL search_path=pg_temp,public");
            assertThat(text(offline,policySql(false,aclApproval))).isEqualTo("CHANGED");
            assertThat(text(offline,"SELECT granted FROM pg_temp.resource_acl")).isEqualTo("t");
            offline.commit();
        }
        try(Connection observer=login("stage6_test")) {
            assertThat(text(observer,"SELECT granted FROM "+schema+".resource_acl WHERE operation='workflow:read'")).isEqualTo("f");
            assertThat(text(observer,"SELECT acl_revision FROM "+schema+".tenant_guards")).isEqualTo("1");
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".workflow_policy_audit")).isEqualTo("1");
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void approvedTenantCannotMutateAnotherTenantsMemberOrResource(boolean membership) throws Exception {
        sql("INSERT INTO "+schema+".tenant_guards VALUES('tenant-b',0)");
        sql("INSERT INTO "+schema+".tenant_memberships VALUES('tenant-b','member-a',true),('tenant-b','foreign-member',true)");
        sql("INSERT INTO "+schema+".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) VALUES('item','foreign-res','tenant-b','member-a','ACTIVE')");
        sql("INSERT INTO "+schema+".resource_acl VALUES('tenant-b','item','foreign-res','member-a','workflow:read',true)");
        ObjectNode approved=(membership?membershipApproval:aclApproval).deepCopy();
        approved.put(membership?"member":"resource",membership?"foreign-member":"foreign-res");
        approved.remove("manifest_digest"); installApproval(membership?MEMBERSHIP:ACL,approved); administrator.commit();
        try(Connection offline=login(provisionerRole)) {
            assertThatThrownBy(()->text(offline,policySql(membership,approved)))
                    .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
            offline.rollback();
        }
        try(Connection observer=login("stage6_test")) {
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".tenant_guards WHERE acl_revision<>0")).isEqualTo("0");
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".tenant_memberships WHERE NOT active")).isEqualTo("0");
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".resource_registry WHERE owner_actor_id<>'member-a'")).isEqualTo("0");
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".resource_acl WHERE NOT granted")).isEqualTo("0");
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".workflow_policy_audit")).isEqualTo("0");
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void auditFailureRollsBackOfflineOwnerMembershipGrantsAndRevision(boolean membership) throws Exception {
        sql("ALTER TABLE "+schema+".workflow_policy_audit ADD CONSTRAINT injected_audit_failure CHECK(resulting_acl_revision=0)");
        administrator.commit();
        try(Connection offline=login(provisionerRole)) {
            assertThatThrownBy(()->text(offline,policySql(membership,membership?membershipApproval:aclApproval)))
                    .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("23514"));
            offline.rollback();
        }
        assertPolicyState(false);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void approvalExpiringWhileWaitingForTenantGuardCannotMutate(boolean membership) throws Exception {
        ObjectNode approval=membership?membershipApproval:aclApproval;
        approval.put("expires_at",Instant.now().plusSeconds(2).toString()); approval.remove("manifest_digest");
        installApproval(membership?MEMBERSHIP:ACL,approval); administrator.commit();
        sql("SELECT tenant_id FROM "+schema+".tenant_guards FOR UPDATE");
        AtomicInteger pid=new AtomicInteger(); CountDownLatch ready=new CountDownLatch(1);
        try(var workers=Executors.newSingleThreadExecutor()) {
            var attempt=workers.submit(()->{
                try(Connection offline=login(provisionerRole)) {
                    pid.set(Integer.parseInt(text(offline,"SELECT pg_backend_pid()"))); ready.countDown();
                    rejected(offline,policySql(membership,approval)); return true;
                }
            });
            try {
                assertThat(ready.await(3,TimeUnit.SECONDS)).isTrue();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(number("SELECT cardinality(pg_blocking_pids("+pid.get()+"))")==0 && System.nanoTime()<deadline) Thread.sleep(25);
                assertThat(number("SELECT cardinality(pg_blocking_pids("+pid.get()+"))")).isPositive();
                while(number("SELECT (clock_timestamp()>="+literal(approval.get("expires_at").asText())+"::timestamptz)::int")==0) Thread.sleep(25);
                administrator.commit();
                assertThat(attempt.get(8,TimeUnit.SECONDS)).isTrue();
            } finally { administrator.rollback(); }
        }
        assertPolicyState(false);
    }

    @Test void approvedNoOpDoesNotAdvanceRevisionOrFabricateAudit() throws Exception {
        aclApproval.put("granted",true);
        aclApproval.remove("manifest_digest");
        installApproval(ACL,aclApproval); administrator.commit();
        try(Connection offline=login(provisionerRole)) {
            assertThat(text(offline,policySql(false,aclApproval))).isEqualTo("NO_CHANGE"); offline.commit();
        }
        assertPolicyState(false);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void approvalReplacedWhileWaitingForTenantGuardCannotMutate(boolean membership) throws Exception {
        ObjectNode approval=membership?membershipApproval:aclApproval;
        sql("SELECT tenant_id FROM "+schema+".tenant_guards FOR UPDATE");
        AtomicInteger pid=new AtomicInteger(); CountDownLatch ready=new CountDownLatch(1);
        try(var workers=Executors.newSingleThreadExecutor()) {
            var attempt=workers.submit(()->{
                try(Connection offline=login(provisionerRole)) {
                    pid.set(Integer.parseInt(text(offline,"SELECT pg_backend_pid()"))); ready.countDown();
                    rejected(offline,policySql(membership,approval)); return true;
                }
            });
            try {
                assertThat(ready.await(3,TimeUnit.SECONDS)).isTrue();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(number("SELECT cardinality(pg_blocking_pids("+pid.get()+"))")==0 && System.nanoTime()<deadline) Thread.sleep(25);
                assertThat(number("SELECT cardinality(pg_blocking_pids("+pid.get()+"))")).isPositive();
                ObjectNode replacement=approval.deepCopy(); replacement.put("event",UUID.randomUUID().toString());
                replacement.remove("manifest_digest"); installApproval(membership?MEMBERSHIP:ACL,replacement);
                administrator.commit();
                assertThat(attempt.get(8,TimeUnit.SECONDS)).isTrue();
            } finally { administrator.rollback(); }
        }
        assertPolicyState(false);
    }

    @Test void realOfflineReactivationDoesNotRestoreOldOwnersOrGrants() throws Exception {
        try(Connection offline=login(provisionerRole)) {
            assertThat(text(offline,policySql(true,membershipApproval))).isEqualTo("CHANGED"); offline.commit();
        }
        membershipApproval.put("active",true).putNull("replacement").put("revision",1)
                .put("event",UUID.randomUUID().toString()).put("correlation",UUID.randomUUID().toString());
        membershipApproval.remove("manifest_digest");
        installApproval(MEMBERSHIP,membershipApproval); administrator.commit();
        try(Connection offline=login(provisionerRole)) {
            assertThat(text(offline,policySql(true,membershipApproval))).isEqualTo("CHANGED"); offline.commit();
        }
        try(Connection observer=login("stage6_test")) {
            assertThat(text(observer,"SELECT active FROM "+schema+".tenant_memberships WHERE actor_id='member-a'")).isEqualTo("t");
            assertThat(text(observer,"SELECT owner_actor_id FROM "+schema+".resource_registry")).isEqualTo("member-b");
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".resource_acl WHERE granted")).isEqualTo("0");
            assertThat(text(observer,"SELECT acl_revision FROM "+schema+".tenant_guards")).isEqualTo("2");
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".workflow_policy_audit")).isEqualTo("2");
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void offlineUnknownCommitReconcilesFreshStateAndAuditWithoutRetry(boolean committed) throws Exception {
        AtomicInteger attempts=new AtomicInteger();
        try(Connection real=login(provisionerRole)) {
            Connection lost=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("commit")) {
                    attempts.incrementAndGet(); if(committed) real.commit();
                    throw new SQLException("simulated acknowledgement loss","08006");
                }
                try { return method.invoke(real,args); } catch(InvocationTargetException e) { throw e.getCause(); }
            });
            assertThat(text(lost,policySql(true,membershipApproval))).isEqualTo("CHANGED");
            assertThatThrownBy(lost::commit).isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("08006"));
        }
        assertThat(attempts).hasValue(1);
        assertPolicyState(committed);
        try(Connection observer=login("stage6_test")) {
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".workflow_policy_audit WHERE audit_event_id="+literal(membershipApproval.get("event").asText())+"::uuid")).isEqualTo(committed?"1":"0");
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void actualOfflineRevokeAndRuntimeWriteSerializeInBothOrders(boolean writeFirst) throws Exception {
        WorkflowInstance proposal=WorkflowInstance.notStarted(INSTANCE_ID,definition(),RESOURCE,CREATED);
        AtomicInteger waiterPid=new AtomicInteger();
        CountDownLatch ready=new CountDownLatch(1);
        try(Connection runtime=login(runtimeRole);Connection offline=login(provisionerRole);var workers=Executors.newSingleThreadExecutor()) {
            String write="SELECT outcome FROM "+schema+".wf_port_create_instance('member-a',true,"+literal(Stage6WorkflowPersistence.snapshot(proposal).toString())+"::jsonb)";
            Connection first=writeFirst?runtime:offline,second=writeFirst?offline:runtime;
            assertThat(text(first,writeFirst?write:policySql(true,membershipApproval))).isEqualTo(writeFirst?"SNAPSHOT":"CHANGED");
            var waiting=workers.submit(()->{
                waiterPid.set(Integer.parseInt(text(second,"SELECT pg_backend_pid()"))); ready.countDown();
                String result=text(second,writeFirst?policySql(true,membershipApproval):write); second.commit(); return result;
            });
            try {
                assertThat(ready.await(3,TimeUnit.SECONDS)).isTrue();
                int firstPid=Integer.parseInt(text(first,"SELECT pg_backend_pid()"));
                assertThat(waiterPid.get()).isNotEqualTo(firstPid);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(number("SELECT cardinality(pg_blocking_pids("+waiterPid.get()+"))")==0 && System.nanoTime()<deadline) Thread.sleep(25);
                assertThat(number("SELECT cardinality(pg_blocking_pids("+waiterPid.get()+"))")).isPositive();
                first.commit();
                assertThat(waiting.get(10,TimeUnit.SECONDS)).isEqualTo(writeFirst?"CHANGED":"HIDDEN");
                try(Connection observer=login("stage6_test")) {
                    int observerPid=Integer.parseInt(text(observer,"SELECT pg_backend_pid()"));
                    assertThat(observerPid).isNotIn(firstPid,waiterPid.get());
                    assertThat(text(observer,"SELECT acl_revision FROM "+schema+".tenant_guards")).isEqualTo("1");
                    assertThat(text(observer,"SELECT count(*) FROM "+schema+".workflow_policy_audit")).isEqualTo("1");
                    System.out.printf("D2_RACE write_first=%s first_pid=%d waiter_pid=%d observer_pid=%d revision=1 audit=1%n",writeFirst,firstPid,waiterPid.get(),observerPid);
                }
            } finally { first.rollback(); }
        }
        assertPolicyState(true);
        assertThat(number("SELECT count(*) FROM "+schema+".workflow_instances")).isEqualTo(writeFirst?1:0);
        assertThat(number("SELECT count(*) FROM "+schema+".workflow_transition_history")).isZero();
    }

    private ObjectNode approval(String action) throws Exception {
        ObjectNode value=JSON.createObjectNode();
        value.put("login",provisionerRole).put("database","stage6_disposable_test").put("schema",schema)
                .put("action",action).put("tenant","tenant-a").put("member","member-a").put("revision",0)
                .put("event",UUID.randomUUID().toString()).put("reason","synthetic-approved-action")
                .put("correlation",UUID.randomUUID().toString()).put("operator_ref","test-only/synthetic-operator")
                .put("fixture_digest",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(Path.of("src/test/java/com/tricore/dxos/infrastructure/workflow/Stage6RuntimeLoginPrivilegeIntegrationTest.java")))))
                .put("not_before",Instant.now().minusSeconds(60).toString()).put("expires_at",Instant.now().plusSeconds(600).toString());
        return value;
    }
    private void installApproval(String signature,ObjectNode value) throws Exception {
        // Pin only nonsecret metadata/digest: pg_proc is readable by unrelated LOGINs.
        if(!value.has("manifest_digest")) value.put("manifest_digest",text("SELECT encode(sha256(convert_to("+literal(value.toString())+"::jsonb::text,'UTF8')),'hex')"));
        ObjectNode metadata=JSON.createObjectNode();
        for(String key:List.of("operator_ref","fixture_digest","not_before","expires_at","manifest_digest"))
            if(value.has(key)) metadata.set(key,value.get(key));
        sql("ALTER FUNCTION "+schema+"."+signature+" SECURITY DEFINER SET stage6.offline_approval="+literal(metadata.toString()));
        sql("GRANT EXECUTE ON FUNCTION "+schema+"."+signature+" TO "+provisionerRole);
    }
    private Connection login(String role) throws SQLException {
        String password=role.equals(outsiderRole)?outsiderPassword:role.equals(provisionerRole)?provisionerPassword
                :role.equals("stage6_test")?System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD"):runtimePassword;
        return login(role,password);
    }
    private Connection login(String role,String password) throws SQLException {
        Properties options=new Properties(); options.setProperty("user",role); options.setProperty("password",password);
        options.setProperty("connectTimeout","5"); options.setProperty("socketTimeout","20");
        Connection connection=DriverManager.getConnection(jdbcUrl,options); connection.setAutoCommit(false); return connection;
    }
    private String policySql(boolean membership,ObjectNode value) {
        return "SELECT "+schema+(membership?".stage6_policy_membership_candidate(":".stage6_policy_acl_candidate(")
                +literal(value.get("tenant").asText())+","+(membership?"":literal(value.get("type").asText())+","+literal(value.get("resource").asText())+",")
                +literal(value.get("member").asText())+","+(membership?value.get("active").asBoolean()+","+(value.hasNonNull("replacement")?literal(value.get("replacement").asText()):"NULL")
                :literal(value.get("operation").asText())+","+value.get("granted").asBoolean())+","+value.get("revision").asLong()+","+literal(value.get("event").asText())+"::uuid,"
                +literal(value.get("reason").asText())+","+literal(value.get("correlation").asText())+")";
    }
    private static String literal(String value) { return "'"+value.replace("'","''")+"'"; }
    private void assertPolicyState(boolean deactivated) throws Exception {
        try(Connection observer=login("stage6_test")) {
            assertThat(text(observer,"SELECT acl_revision FROM "+schema+".tenant_guards")).isEqualTo(deactivated?"1":"0");
            assertThat(text(observer,"SELECT active FROM "+schema+".tenant_memberships WHERE actor_id='member-a'")).isEqualTo(deactivated?"f":"t");
            assertThat(text(observer,"SELECT owner_actor_id FROM "+schema+".resource_registry")).isEqualTo(deactivated?"member-b":"member-a");
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".resource_acl WHERE granted")).isEqualTo(deactivated?"0":"4");
            assertThat(text(observer,"SELECT count(*) FROM "+schema+".workflow_policy_audit")).isEqualTo(deactivated?"1":"0");
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
