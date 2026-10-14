package com.tricore.dxos.infrastructure.workflow;

import com.tricore.dxos.core.workflow.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_JDBC_URL", matches = ".+")
class WorkflowIsolationIntegrationTest {
    @TempDir Path bundle;
    DataSource admin, owner, runtime;
    WorkflowSmokeRunner runner;
    boolean created;
    String probe = "wf25_probe_" + UUID.randomUUID().toString().replace("-", "");
    static final String S = WorkflowSmokeRunner.SCHEMA;

    @BeforeEach void setup() throws Exception {
        admin = source(null, true, null);
        owner = source(S, true, null);
        runtime = source(S, false, null);
        try (Connection c = admin.getConnection(); Statement st = c.createStatement()) {
            // Disposable loopback database only; never execute this fixture on shared Supabase.
            st.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon NOLOGIN; END IF; IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated NOLOGIN; END IF; IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='service_role') THEN CREATE ROLE service_role NOLOGIN; END IF; END $$");
            st.execute("CREATE SCHEMA " + S);
            created = true;
            st.execute("GRANT USAGE ON SCHEMA " + S + " TO " + role());
            st.execute("CREATE TABLE public." + probe + "(value text)");
            st.execute("INSERT INTO public." + probe + " VALUES ('preserved')");
            st.execute("GRANT SELECT ON public." + probe + " TO anon, authenticated, service_role");
        }
        for (String name : List.of("V3__create_workflow_persistence.sql", "V4__seed_workflow_definitions.sql"))
            Files.copy(Path.of("src/main/resources/db/migration", name), bundle.resolve(name));
        runner = new WorkflowSmokeRunner(owner, runtime, bundle);
    }

    @AfterEach void cleanup() throws Exception {
        if (created) try (Connection c = admin.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA " + S + " CASCADE");
            st.execute("DROP TABLE IF EXISTS public." + probe);
        }
    }

    @Test void migrationAndSmokeStayPrivateAndPersistAcrossNewConnections() throws Exception {
        String outside = outsideDigest();
        assertThat(runner.check()).isEqualTo("EMPTY");
        runner.migrate();
        grantRuntime();
        assertThat(runner.check()).isEqualTo("READY");
        assertThat(count("workflow_definitions")).isEqualTo(2);
        assertThat(count("workflow_definition_states")).isEqualTo(7);
        assertThat(count("workflow_definition_transitions")).isEqualTo(5);
        assertThat(count("flyway_schema_history")).isEqualTo(2);
        assertThat(scalar(admin, "SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relname LIKE 'workflow_%'")).isZero();
        assertThat(scalar(admin, "SELECT count(*) FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname IN ('reject_workflow_definition_change','require_workflow_definition_provisioning')")).isZero();
        runner.smoke("local-isolation");
        assertThat(count("workflow_instances")).isEqualTo(2);
        assertThat(count("workflow_transition_history")).isEqualTo(4);
        new WorkflowSmokeRunner(source(S, true, null), source(S, false, null), bundle).read("local-isolation");
        assertThatThrownBy(() -> runner.smoke("local-isolation")).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("workflow_instances")).isEqualTo(2);
        assertThat(outsideDigest()).isEqualTo(outside);
    }

    @Test void existingUnknownObjectsPreventMigration() throws Exception {
        execute("CREATE TABLE " + S + ".unexpected(id int)");
        assertThatThrownBy(runner::migrate).isInstanceOf(IllegalArgumentException.class);
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace AND relname='flyway_schema_history'")).isZero();
    }

    @Test void migratedSchemaCannotBeMigratedAgainByRunner() throws Exception {
        runner.migrate();
        assertThatThrownBy(runner::migrate).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("flyway_schema_history")).isEqualTo(2);
    }

    @Test void checksumMismatchStopsBeforeWorkflowWrites() throws Exception {
        runner.migrate(); grantRuntime();
        execute("UPDATE " + S + ".flyway_schema_history SET checksum=checksum+1 WHERE version='4'");
        assertThatThrownBy(() -> runner.smoke("checksum")).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("workflow_instances")).isZero();
    }

    @Test void missingRuntimePermissionsStopBeforeWrites() throws Exception {
        runner.migrate();
        assertThatThrownBy(() -> runner.smoke("permission")).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("workflow_instances")).isZero();
    }

    @Test void runtimeCreatePrivilegeStopsBeforeDdl() throws Exception {
        execute("GRANT CREATE ON SCHEMA " + S + " TO " + role());
        assertThatThrownBy(runner::migrate).isInstanceOf(IllegalArgumentException.class);
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
        assertThat(scalar(admin, "SELECT count(*) FROM pg_class WHERE relnamespace='public'::regnamespace AND relname='flyway_schema_history'")).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"workflow_definitions", "workflow_definition_states",
            "workflow_definition_transitions", "workflow_instances", "workflow_transition_history"})
    void runtimeTriggerPrivilegeStopsBeforeWrites(String table) throws Exception {
        runner.migrate(); grantRuntime();
        execute("GRANT TRIGGER ON " + S + "." + table + " TO " + role());
        assertThat(scalar(runtime, "SELECT pg_catalog.has_table_privilege(current_user, '" + S + "." + table + "', 'TRIGGER')::int")).isEqualTo(1);
        assertThatThrownBy(runner::check).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Runtime table permissions rejected");
        assertThatThrownBy(() -> runner.smoke("trigger")).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("workflow_instances")).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"workflow_definitions", "workflow_definition_states",
            "workflow_definition_transitions", "workflow_instances", "workflow_transition_history"})
    void inheritedRuntimeTriggerPrivilegeStopsBeforeWrites(String table) throws Exception {
        runner.migrate(); grantRuntime();
        String inheritedRole = "wf25_trigger_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE ROLE " + inheritedRole + " NOLOGIN");
        try {
            execute("GRANT TRIGGER ON " + S + "." + table + " TO " + inheritedRole);
            execute("GRANT " + inheritedRole + " TO " + role() + " WITH INHERIT TRUE, SET FALSE");
            assertThat(scalar(runtime, "SELECT pg_catalog.has_table_privilege(current_user, '" + S + "." + table + "', 'TRIGGER')::int")).isEqualTo(1);
            assertThatThrownBy(runner::check).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Runtime table permissions rejected");
            assertThatThrownBy(() -> runner.smoke("inherited-trigger")).isInstanceOf(IllegalArgumentException.class);
            assertThat(count("workflow_instances")).isZero();
        } finally {
            execute("REVOKE " + inheritedRole + " FROM " + role());
            execute("REVOKE TRIGGER ON " + S + "." + table + " FROM " + inheritedRole);
            execute("DROP ROLE " + inheritedRole);
        }
    }

    @Test void runtimeWithoutTriggerPrivilegeIsAccepted() throws Exception {
        runner.migrate(); grantRuntime();
        for (String table : List.of("workflow_definitions", "workflow_definition_states",
                "workflow_definition_transitions", "workflow_instances", "workflow_transition_history")) {
            assertThat(scalar(runtime, "SELECT pg_catalog.has_table_privilege(current_user, '" + S + "." + table + "', 'TRIGGER')::int")).isZero();
        }
        assertThat(runner.check()).isEqualTo("READY");
    }

    @Test void runtimeOwnerIsRejected() throws Exception {
        runner.migrate();
        var ownedRuntime = new WorkflowSmokeRunner(owner, owner, bundle);
        assertThatThrownBy(ownedRuntime::check).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Runtime role or transaction mode rejected");
    }

    @Test void smokeRunnerRejectsWrongRuntimeSchema() throws Exception {
        runner.migrate(); grantRuntime();
        var wrongSchema = new WorkflowSmokeRunner(owner, source("public", false, null), bundle);
        assertThatThrownBy(wrongSchema::check).isInstanceOf(SQLException.class)
                .hasMessage("Workflow connection rejected");
    }

    @Test void jdbcCatalogPreflightIsReadOnlyOnPostgres() throws Exception {
        runner.migrate(); grantRuntime();
        String outsideBefore = outsideDigest();
        var output = new java.io.ByteArrayOutputStream();
        try (Connection connection = runtime.getConnection()) {
            WorkflowReadOnlyPreflight.inspect(connection, new java.io.PrintStream(output));
            assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                    .contains("transaction_read_only=on; expected_schema_present=true", "[flyway_history]");
            try (var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute("INSERT INTO " + S + ".workflow_instances DEFAULT VALUES"))
                        .isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo("25006"));
            }
            connection.rollback();
        }
        assertThat(count("workflow_instances")).isZero();
        assertThat(outsideDigest()).isEqualTo(outsideBefore);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_CA_CERT", matches = ".+")
    void jdbcEntrypointKeepsEveryCatalogQueryInVerifiedReadOnlyTransaction() throws Exception {
        String outsideBefore = outsideDigest();
        var output = new java.io.ByteArrayOutputStream();
        var errors = new java.io.ByteArrayOutputStream();
        var environment = Map.of("DB_HOST", "local-fixture.pooler.supabase.com", "DB_PORT", "5432",
                "DB_NAME", "workflow_test", "DB_USER", env("WORKFLOW_TEST_DB_USER"),
                "DB_PASSWORD", env("WORKFLOW_TEST_DB_PASSWORD"), "PGSSLROOTCERT", env("WORKFLOW_TEST_CA_CERT"));
        var local = (DriverManagerDataSource) source(S, false, null);
        local.setUrl(local.getUrl().replace("127.0.0.1", "localhost"));
        int exit = WorkflowReadOnlyPreflight.run(environment, (ignoredUrl, properties) -> {
            local.setConnectionProperties(properties);
            Connection actual = local.getConnection();
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getCatalog") || method.getName().equals("createStatement")) {
                            if (actual.getAutoCommit()) throw new SQLException("Catalog outside read-only transaction", "25001");
                            try (var check = actual.createStatement(); var rows = check.executeQuery(
                                    "SELECT pg_catalog.current_setting('transaction_read_only')")) {
                                assertThat(rows.next()).isTrue();
                                assertThat(rows.getString(1)).isEqualTo("on");
                            }
                        }
                        try {
                            return method.invoke(actual, arguments);
                        } catch (java.lang.reflect.InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }, new java.io.PrintStream(output), new java.io.PrintStream(errors));
        assertThat(exit).as(errors.toString(java.nio.charset.StandardCharsets.UTF_8)).isZero();
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("transaction_read_only=on", "client_pooler_tls=VERIFIED_VERIFY_FULL",
                        "pooler_backend_ssl=TRUE", "READ_ONLY_CATALOG_COMPLETE");
        assertThat(outsideDigest()).isEqualTo(outsideBefore);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_WRONG_CA_CERT", matches = ".+")
    void verifyFullRejectsUntrustedCaAndWrongHostname() throws Exception {
        var environment = Map.of("DB_HOST", "local-fixture.pooler.supabase.com", "DB_PORT", "5432",
                "DB_NAME", "workflow_test", "DB_USER", env("WORKFLOW_TEST_DB_USER"),
                "DB_PASSWORD", env("WORKFLOW_TEST_DB_PASSWORD"), "PGSSLROOTCERT", env("WORKFLOW_TEST_CA_CERT"));
        var properties = WorkflowReadOnlyPreflight.connectionProperties(environment);
        String loopbackUrl = env("WORKFLOW_TEST_JDBC_URL");
        String matchingUrl = loopbackUrl.replace("127.0.0.1", "localhost");
        try (Connection connected = DriverManager.getConnection(matchingUrl, properties)) {
            assertThat(connected.isClosed()).isFalse();
        }

        var badCa = new Properties();
        badCa.putAll(properties);
        badCa.setProperty("sslrootcert", env("WORKFLOW_TEST_WRONG_CA_CERT"));
        assertThatThrownBy(() -> DriverManager.getConnection(matchingUrl, badCa))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> DriverManager.getConnection(loopbackUrl, properties))
                .isInstanceOf(SQLException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FALSE", "NULL", "NO_ROW"})
    @EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_CA_CERT", matches = ".+")
    void verifiedJdbcTlsAcceptsUnverifiedBackendSslStatus(String backendSsl) throws Exception {
        var environment = Map.of("DB_HOST", "local-fixture.pooler.supabase.com", "DB_PORT", "5432",
                "DB_NAME", "workflow_test", "DB_USER", env("WORKFLOW_TEST_DB_USER"),
                "DB_PASSWORD", env("WORKFLOW_TEST_DB_PASSWORD"), "PGSSLROOTCERT", env("WORKFLOW_TEST_CA_CERT"));
        var output = new java.io.ByteArrayOutputStream();
        var errors = new java.io.ByteArrayOutputStream();
        String localUrl = env("WORKFLOW_TEST_JDBC_URL").replace("127.0.0.1", "localhost");
        int exit = WorkflowReadOnlyPreflight.run(environment, (ignoredUrl, properties) -> {
            Connection actual = DriverManager.getConnection(localUrl, properties);
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        try {
                            if (method.getName().equals("createStatement")) {
                                Statement statement = (Statement) method.invoke(actual, args);
                                return java.lang.reflect.Proxy.newProxyInstance(Statement.class.getClassLoader(),
                                        new Class<?>[]{Statement.class}, (statementProxy, statementMethod, statementArgs) -> {
                                            if (statementMethod.getName().equals("executeQuery")
                                                    && ((String) statementArgs[0]).contains("SELECT ssl FROM pg_catalog.pg_stat_ssl")) {
                                                ResultSet rows = org.mockito.Mockito.mock(ResultSet.class);
                                                org.mockito.Mockito.when(rows.next()).thenReturn(!backendSsl.equals("NO_ROW"), false);
                                                org.mockito.Mockito.when(rows.getBoolean(1)).thenReturn(backendSsl.equals("TRUE"));
                                                org.mockito.Mockito.when(rows.wasNull()).thenReturn(backendSsl.equals("NULL"));
                                                return rows;
                                            }
                                            try { return statementMethod.invoke(statement, statementArgs); }
                                            catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                                        });
                            }
                            return method.invoke(actual, args);
                        } catch (java.lang.reflect.InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }, new java.io.PrintStream(output), new java.io.PrintStream(errors));
        assertThat(exit).as(errors.toString(java.nio.charset.StandardCharsets.UTF_8)).isZero();
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("client_pooler_tls=VERIFIED_VERIFY_FULL", "pooler_backend_ssl=" + backendSsl,
                        "READ_ONLY_CATALOG_COMPLETE");
    }

    @Test void jdbcCatalogPreflightDoesNotCreateMissingSchema() throws Exception {
        String outsideBefore = outsideDigest();
        execute("ALTER SCHEMA " + S + " RENAME TO " + S + "_missing");
        try {
            var output = new java.io.ByteArrayOutputStream();
            try (Connection connection = runtime.getConnection()) {
                WorkflowReadOnlyPreflight.inspect(connection, new java.io.PrintStream(output));
            }
            assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                    .contains("transaction_read_only=on; expected_schema_present=false", "flyway_history=ABSENT");
            assertThat(scalar(admin, "SELECT (pg_catalog.to_regnamespace('" + S + "') IS NOT NULL)::int")).isZero();
        } finally {
            execute("ALTER SCHEMA " + S + "_missing RENAME TO " + S);
        }
        assertThat(outsideDigest()).isEqualTo(outsideBefore);
    }

    @Test void jdbcCatalogPreflightDoesNotPrintCustomFunctionSettings() throws Exception {
        execute("CREATE FUNCTION " + S + ".preflight_configuration_probe() RETURNS integer LANGUAGE sql "
                + "SET dxos.secret='sensitive-fixture' AS 'SELECT 1'");
        var output = new java.io.ByteArrayOutputStream();
        try (Connection connection = runtime.getConnection()) {
            WorkflowReadOnlyPreflight.inspect(connection, new java.io.PrintStream(output));
        }
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("preflight_configuration_probe").doesNotContain("sensitive-fixture", "dxos.secret");
    }

    @ParameterizedTest @ValueSource(strings={"public", "other_schema", S+",public"})
    void urlOverrideRejectedBeforeWriting(String override) throws Exception {
        runner.migrate(); grantRuntime();
        var adapter = new PostgresWorkflowPersistence(source(S, false, override), S);
        var definition = new PostgresWorkflowPersistence(owner, S).loadDefinition("document-review", 1);
        var initial = WorkflowInstance.notStarted("rejected", definition, new ResourceReference("test", "rejected"), java.time.Instant.EPOCH);
        assertThatThrownBy(() -> adapter.createInstance(initial))
                .isInstanceOfSatisfying(WorkflowException.class, e -> assertThat(e.code()).isEqualTo(WorkflowErrorCode.OPERATION_FAILURE));
        assertThat(count("workflow_instances")).isZero();
    }

    @Test void wrongPropertiesSchemaRejectedBeforeReads() throws Exception {
        runner.migrate(); grantRuntime();
        var adapter = new PostgresWorkflowPersistence(source("public", false, null), S);
        assertThatThrownBy(() -> adapter.loadDefinition("document-review", 1))
                .isInstanceOfSatisfying(WorkflowException.class, e -> assertThat(e.code()).isEqualTo(WorkflowErrorCode.OPERATION_FAILURE));
    }

    @Test void unapprovedBundleRejectedBeforeConnection() throws Exception {
        Files.writeString(bundle.resolve("V4__seed_workflow_definitions.sql"), "-- changed", StandardOpenOption.APPEND);
        assertThatThrownBy(() -> new WorkflowSmokeRunner(owner, runtime, bundle)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void extraMigrationRejectedBeforeConnection() throws Exception {
        Files.writeString(bundle.resolve("V1__unexpected.sql"), "SELECT 1");
        assertThatThrownBy(() -> new WorkflowSmokeRunner(owner, runtime, bundle)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unsafeMigrationConfigurationRejectedBeforeDdl() throws Exception {
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S+"\"; DROP SCHEMA public; --", "filesystem:"+bundle)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:"+bundle).executeInTransaction(false).load().migrate()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:"+bundle).defaultSchema("public").load().migrate()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:"+bundle).table("other_history").load().migrate()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:"+bundle).placeholders(Map.of("flyway:defaultSchema", "public")).load().migrate()).isInstanceOf(RuntimeException.class);
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @Test void customDefaultPrivilegesRequireReviewBeforeDdl() throws Exception {
        execute("ALTER DEFAULT PRIVILEGES IN SCHEMA " + S + " GRANT SELECT ON TABLES TO anon");
        assertThatThrownBy(runner::migrate).isInstanceOf(IllegalArgumentException.class);
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @Test void enabledEventTriggerRequiresReviewBeforeDdl() throws Exception {
        execute("CREATE FUNCTION " + S + ".observe_ddl() RETURNS event_trigger LANGUAGE plpgsql AS $$ BEGIN END; $$");
        execute("CREATE EVENT TRIGGER wf25_local_event ON ddl_command_end EXECUTE FUNCTION " + S + ".observe_ddl()");
        assertThatThrownBy(runner::migrate).isInstanceOf(SQLException.class).hasMessage("Workflow connection rejected");
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @Test void flywayRejectsTriggerCreatedAfterConfigurationBeforeDdl() throws Exception {
        var flyway = WorkflowMigrations.configure(owner, S, "filesystem:" + bundle).target("4").load();
        String function = "public." + probe + "_observe";
        execute("CREATE FUNCTION " + function + "() RETURNS event_trigger LANGUAGE plpgsql AS $$ BEGIN END; $$");
        try {
            execute("CREATE EVENT TRIGGER wf25_boundary_event ON ddl_command_end EXECUTE FUNCTION " + function + "()");
            assertThatThrownBy(flyway::migrate).isInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("Workflow connection rejected");
            assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
        } finally {
            execute("DROP EVENT TRIGGER IF EXISTS wf25_boundary_event");
            execute("DROP FUNCTION " + function + "()");
        }
    }

    @Test void flywayRejectsBundleChangedAfterConfigurationBeforeDdl() throws Exception {
        var flyway = WorkflowMigrations.configure(owner, S, "filesystem:" + bundle).target("4").load();
        Files.writeString(bundle.resolve("V4__seed_workflow_definitions.sql"), "-- drift", StandardOpenOption.APPEND);
        assertThatThrownBy(flyway::migrate).isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Workflow connection rejected");
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @Test void flywayRejectsUnreviewedInitializationAndCallbackConfiguration() throws Exception {
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:" + bundle)
                .initSql("CREATE TABLE public.unreviewed_init(id int)").load().migrate()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:" + bundle)
                .callbacks(new org.flywaydb.core.api.callback.Callback[0]).load().migrate()).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:" + bundle)
                .skipDefaultCallbacks(false).load().migrate()).isInstanceOf(RuntimeException.class);
        assertThat(scalar(admin, "SELECT (pg_catalog.to_regclass('public.unreviewed_init') IS NOT NULL)::int")).isZero();
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @Test void flywayRejectsSpoofedCallbackWithTheGuardNameBeforeDdl() throws Exception {
        var replacement = new org.flywaydb.core.api.callback.Callback() {
            public boolean supports(org.flywaydb.core.api.callback.Event event, org.flywaydb.core.api.callback.Context context) {
                return event == org.flywaydb.core.api.callback.Event.BEFORE_MIGRATE;
            }
            public boolean canHandleInTransaction(org.flywaydb.core.api.callback.Event event, org.flywaydb.core.api.callback.Context context) {
                return true;
            }
            public String getCallbackName() { return "workflow-schema-isolation"; }
            public void handle(org.flywaydb.core.api.callback.Event event, org.flywaydb.core.api.callback.Context context) {
                try (var statement = context.getConnection().createStatement()) {
                    statement.execute("CREATE TABLE public.unreviewed_callback(id int)");
                } catch (SQLException failure) { throw new IllegalStateException(failure); }
            }
        };
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:" + bundle)
                .callbacks(replacement).load().migrate()).isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Workflow connection rejected");
        assertThat(scalar(admin, "SELECT (pg_catalog.to_regclass('public.unreviewed_callback') IS NOT NULL)::int")).isZero();
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @Test void flywayRejectsCustomResourceProviderWithAnApprovedScriptName() throws Exception {
        var resource = org.mockito.Mockito.mock(org.flywaydb.core.api.resource.LoadableResource.class);
        org.mockito.Mockito.when(resource.getFilename()).thenReturn("V3__create_workflow_persistence.sql");
        org.mockito.Mockito.when(resource.getRelativePath()).thenReturn("V3__create_workflow_persistence.sql");
        org.mockito.Mockito.when(resource.getAbsolutePath()).thenReturn("V3__create_workflow_persistence.sql");
        org.mockito.Mockito.when(resource.read()).thenAnswer(ignored ->
                new java.io.StringReader("CREATE TABLE public.unreviewed_provider(id int)"));
        var provider = org.mockito.Mockito.mock(org.flywaydb.core.api.ResourceProvider.class);
        org.mockito.Mockito.when(provider.getResources(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(List.of(resource));
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:" + bundle)
                .resourceProvider(provider).load().migrate()).isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Workflow connection rejected");
        assertThat(scalar(admin, "SELECT (pg_catalog.to_regclass('public.unreviewed_provider') IS NOT NULL)::int")).isZero();
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @Test void flywayRejectsJavaMigrationsAndWorkingDirectoryOverrides() throws Exception {
        var migration = org.mockito.Mockito.mock(org.flywaydb.core.api.migration.JavaMigration.class);
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:" + bundle)
                .javaMigrations(migration).load().migrate()).isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Workflow connection rejected");
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:" + bundle)
                .workingDirectory(bundle.getParent().toString()).load().migrate()).isInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("Workflow connection rejected");
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @Test void flywayRejectsClassLoaderAndEncodingDrift() throws Exception {
        var configuration = WorkflowMigrations.configure(owner, S, "filesystem:" + bundle);
        ClassLoader replacement = new ClassLoader(configuration.getClassLoader()) {};
        var changed = new org.flywaydb.core.api.configuration.ClassicConfiguration(configuration);
        changed.setClassLoader(replacement);
        assertThatThrownBy(() -> configuration.configuration(changed).load().migrate())
                .isInstanceOf(RuntimeException.class).hasRootCauseMessage("Workflow connection rejected");
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:" + bundle)
                .encoding(java.nio.charset.StandardCharsets.UTF_16).load().migrate())
                .isInstanceOf(RuntimeException.class).hasRootCauseMessage("Workflow connection rejected");
        assertThatThrownBy(() -> WorkflowMigrations.configure(owner, S, "filesystem:" + bundle)
                .detectEncoding(true).load().migrate())
                .isInstanceOf(RuntimeException.class).hasRootCauseMessage("Workflow connection rejected");
        assertThat(scalar(owner, "SELECT count(*) FROM pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @Test void localDisposableFlagCannotAuthorizeARemoteEmptyInventory() throws Exception {
        try (Connection actual = owner.getConnection()) {
            var metadata = org.mockito.Mockito.mock(java.sql.DatabaseMetaData.class);
            org.mockito.Mockito.when(metadata.getURL()).thenReturn(
                    actual.getMetaData().getURL().replace("127.0.0.1", "remote.invalid"));
            Connection remoteLabel = (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) return metadata;
                        try { return method.invoke(actual, args); }
                        catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                    });
            assertThatThrownBy(() -> WorkflowEventTriggerGuard.REVIEWED.verify(remoteLabel, S))
                    .isInstanceOf(SQLException.class).hasMessage("Workflow event-trigger guard rejected");
        }
    }

    @Test void smokeAndRuntimeRejectTriggerAddedAfterMigrationBeforeWriting() throws Exception {
        runner.migrate(); grantRuntime();
        var persistence = new PostgresWorkflowPersistence(runtime, S);
        var workflow = new WorkflowRuntime(persistence, java.time.Clock.systemUTC());
        String function = "public." + probe + "_observe";
        execute("CREATE FUNCTION " + function + "() RETURNS event_trigger LANGUAGE plpgsql AS $$ BEGIN END; $$");
        try {
            execute("CREATE EVENT TRIGGER wf25_boundary_event ON ddl_command_end EXECUTE FUNCTION " + function + "()");
            assertThatThrownBy(() -> runner.smoke("guard-drift")).isInstanceOf(SQLException.class)
                    .hasMessage("Workflow connection rejected");
            assertThatThrownBy(() -> workflow.createInstance("guard-drift", "document-review", 1,
                    new ResourceReference("guard", probe))).isInstanceOfSatisfying(WorkflowException.class,
                    failure -> assertThat(failure.code()).isEqualTo(WorkflowErrorCode.OPERATION_FAILURE));
            assertThat(count("workflow_instances")).isZero();
        } finally {
            execute("DROP EVENT TRIGGER IF EXISTS wf25_boundary_event");
            execute("DROP FUNCTION " + function + "()");
        }
    }


    @Test void immutableFunctionsAndAclRemainEffective() throws Exception {
        runner.migrate(); grantRuntime();
        assertThat(scalar(owner, "SELECT count(*) FROM pg_proc WHERE pronamespace='" + S + "'::regnamespace AND NOT prosecdef AND proconfig @> ARRAY['search_path=pg_catalog, pg_temp']")).isEqualTo(2);
        assertThat(scalar(owner, "SELECT count(*) FROM pg_proc p CROSS JOIN pg_roles r WHERE p.pronamespace='" + S + "'::regnamespace AND r.rolname IN ('anon','authenticated','service_role') AND has_function_privilege(r.oid,p.oid,'EXECUTE')")).isZero();
        try (Connection c=owner.getConnection(); Statement st=c.createStatement()) {
            st.execute("CREATE TEMP TABLE workflow_definitions (definition_id text, definition_version int, provisioning_transaction bigint)");
            st.execute("INSERT INTO pg_temp.workflow_definitions VALUES ('document-review',1,txid_current())");
            assertThatThrownBy(() -> st.execute("INSERT INTO " + S + ".workflow_definition_states VALUES ('document-review',1,'FORGED')")).isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            assertThatThrownBy(() -> st.execute("UPDATE " + S + ".workflow_definitions SET initial_state='FORGED' WHERE definition_id='document-review'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.execute("TRUNCATE " + S + ".workflow_definitions CASCADE")).isInstanceOf(SQLException.class);
        }
        assertThat(count("workflow_definitions")).isEqualTo(2);
    }

    void grantRuntime() throws SQLException {
        execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + S + " TO " + role());
        execute("GRANT INSERT ON " + S + ".workflow_instances, " + S + ".workflow_transition_history TO " + role());
        execute("GRANT UPDATE (current_state,lifecycle,runtime_version,updated_at_seconds,updated_at_nanos) ON " + S + ".workflow_instances TO " + role());
    }

    void execute(String sql) throws SQLException {
        try (Connection c=owner.getConnection(); Statement s=c.createStatement()) { s.execute(sql); }
    }
    long count(String table) throws SQLException { return scalar(owner, "SELECT count(*) FROM " + S + "." + table); }
    static long scalar(DataSource source, String sql) throws SQLException {
        try (Connection c=source.getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { r.next(); return r.getLong(1); }
    }
    String outsideDigest() throws SQLException {
        String sql = "SELECT concat((SELECT string_agg(d::text,',' ORDER BY oid) FROM pg_default_acl d),(SELECT string_agg(r::text,',' ORDER BY oid) FROM pg_roles r),(SELECT string_agg(m::text,',' ORDER BY roleid,member,grantor) FROM pg_auth_members m),(SELECT relacl::text FROM pg_class WHERE oid='public."+probe+"'::regclass),(SELECT value FROM public."+probe+"))";
        try (Connection c=admin.getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery(sql)) { r.next(); return r.getString(1); }
    }
    static String role() { return "\"" + env("WORKFLOW_TEST_DB_USER").replace("\"", "\"\"") + "\""; }
    static String env(String key) { String v=System.getenv(key); if(v==null || v.isBlank()) throw new IllegalArgumentException("Missing local variable: "+key); return v; }
    static DataSource source(String schema, boolean migrator, String override) {
        String url=env("WORKFLOW_TEST_JDBC_URL");
        if(!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost|\\[::1\\]):[0-9]+/[A-Za-z0-9_]+")) throw new IllegalArgumentException("Disposable loopback database required");
        String prefix=migrator && System.getenv("WORKFLOW_TEST_MIGRATION_USER")!=null ? "WORKFLOW_TEST_MIGRATION_" : "WORKFLOW_TEST_DB_";
        var ds=new DriverManagerDataSource(url+(override==null?"":"?currentSchema="+override),env(prefix+"USER"),env(prefix+"PASSWORD"));
        Properties p=new Properties(); if(schema!=null)p.setProperty("currentSchema",schema+",pg_catalog,pg_temp"); ds.setConnectionProperties(p);
        return ds;
    }
}
