package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Synthetic fixture on a disposable loopback server; never reads or imports private cloud captures. */
@EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_JDBC_URL", matches = ".+")
class WorkflowEventTriggerGuardIntegrationTest {
    private static final String SCHEMA = WorkflowSmokeRunner.SCHEMA;
    private static final Map<String, String> FUNCTIONS = new LinkedHashMap<>(Map.of(
            "issue_pg_cron_access", "grant_pg_cron_access", "issue_pg_graphql_access", "grant_pg_graphql_access",
            "issue_pg_net_access", "grant_pg_net_access", "issue_graphql_placeholder", "set_graphql_placeholder",
            "pgrst_ddl_watch", "pgrst_ddl_watch", "pgrst_drop_watch", "pgrst_drop_watch"));
    private ObjectNode approved;
    private WorkflowEventTriggerGuard guard;
    private boolean schemaCreated, ownerCreated, extensionsCreated;
    private final java.util.Set<String> createdTriggers = new java.util.HashSet<>();
    @TempDir java.nio.file.Path bundle;

    @BeforeEach void fixture() throws Exception {
        execute("CREATE SCHEMA " + SCHEMA);
        schemaCreated = true;
        execute("CREATE ROLE supabase_admin SUPERUSER NOLOGIN");
        ownerCreated = true;
        execute("CREATE SCHEMA extensions AUTHORIZATION supabase_admin");
        extensionsCreated = true;
        for (var item : FUNCTIONS.entrySet()) {
            execute("CREATE FUNCTION extensions." + item.getValue()
                    + "() RETURNS event_trigger LANGUAGE plpgsql SET search_path='' AS $$ BEGIN END; $$");
            execute("ALTER FUNCTION extensions." + item.getValue() + "() OWNER TO supabase_admin");
            boolean drop = item.getKey().equals("pgrst_drop_watch") || item.getKey().equals("issue_graphql_placeholder");
            String tags = item.getKey().startsWith("pgrst_") ? ""
                    : " WHEN TAG IN ('" + (drop ? "DROP EXTENSION" : "CREATE EXTENSION") + "')";
            execute("CREATE EVENT TRIGGER " + item.getKey() + " ON " + (drop ? "sql_drop" : "ddl_command_end")
                    + tags + " EXECUTE FUNCTION extensions." + item.getValue() + "()");
            createdTriggers.add(item.getKey());
            execute("ALTER EVENT TRIGGER " + item.getKey() + " OWNER TO supabase_admin");
        }
        try (Connection connection = connection()) {
            var metadata = new ObjectMapper().valueToTree(WorkflowEventTriggerReview.liveMetadata(connection,
                    List.of("pg_catalog.pg_event_trigger_ddl_commands()", "pg_catalog.pg_event_trigger_dropped_objects()")));
            approved = WorkflowEventTriggerGuard.MANIFEST.deepCopy();
            approved.put("serverVersion", metadata.path("session").path("version").asText());
            // This trusted baseline is only for the explicit no-op fixture above; no runtime manifest override exists.
            approved.set("inventory", WorkflowEventTriggerGuard.inventory(metadata));
            guard = new WorkflowEventTriggerGuard(approved);
            guard.verify(connection, SCHEMA);
        }
    }

    @AfterEach void cleanup() throws Exception {
        for (String name : createdTriggers) execute("DROP EVENT TRIGGER IF EXISTS " + name);
        if (extensionsCreated) execute("DROP SCHEMA extensions CASCADE");
        if (ownerCreated) execute("DROP ROLE supabase_admin");
        if (schemaCreated) execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test void approvedFixtureUsesLiveHelperSignaturesInsteadOfCapturedOids() throws Exception {
        approved.withArray("helperEvidence").forEach(helper -> ((ObjectNode) helper).put("observedOid", 1));
        try (Connection connection = connection()) {
            new WorkflowEventTriggerGuard(approved).verify(connection, SCHEMA);
        }
    }

    @Test void approvedMigrationReadOnlyPropertiesPassTheRealCatalogGuard() throws Exception {
        String url=WorkflowIsolationIntegrationTest.env("WORKFLOW_TEST_JDBC_URL");
        if(!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[a-z][a-z0-9_]*"))
            throw new IllegalArgumentException("Disposable loopback required");
        var env=Map.of("DB_HOST",WorkflowSchemaBootstrap.HOST,"DB_PORT","5432","DB_NAME","postgres",
                "DB_USER","postgres","DB_PASSWORD",WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_DB_PASSWORD"),
                "PGSSLROOTCERT",WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_CA_CERT"),
                "WORKFLOW_FLYWAY_APPROVAL",SCHEMA);
        var original=WorkflowApprovedMigration.properties(env,false);
        original.setProperty("currentSchema","pg_catalog,pg_temp");
        try(Connection c=java.sql.DriverManager.getConnection(url.replace("127.0.0.1","localhost"),original)) {
            WorkflowSchemaBootstrap.beginReadOnly(c);
            try {
                assertThatThrownBy(()->guard.verifyReadOnly(c,SCHEMA))
                        .isInstanceOfSatisfying(SQLException.class,e->assertThat(e.getSQLState()).isEqualTo("42501"));
            } finally {c.rollback();}
        }
        try(Connection c=java.sql.DriverManager.getConnection(url.replace("127.0.0.1","localhost"),
                WorkflowApprovedMigration.properties(env,false))) {
            WorkflowSchemaBootstrap.beginReadOnly(c);
            try { guard.verifyReadOnly(c,SCHEMA); }
            finally { c.rollback(); }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CREATE EVENT TRIGGER wf25_unknown ON ddl_command_end EXECUTE FUNCTION extensions.pgrst_ddl_watch()",
            "ALTER EVENT TRIGGER pgrst_ddl_watch RENAME TO wf25_unknown",
            "ALTER EVENT TRIGGER pgrst_ddl_watch OWNER TO CURRENT_USER",
            "ALTER EVENT TRIGGER pgrst_ddl_watch ENABLE ALWAYS",
            "ALTER EVENT TRIGGER pgrst_ddl_watch DISABLE",
            "CREATE OR REPLACE FUNCTION extensions.pgrst_ddl_watch() RETURNS event_trigger LANGUAGE plpgsql SET search_path='' AS $$ BEGIN PERFORM 1; END; $$",
            "ALTER FUNCTION extensions.pgrst_ddl_watch() SET dxos.fixture_token='fixture_value'",
            "ALTER FUNCTION extensions.pgrst_ddl_watch() SECURITY DEFINER",
            "REVOKE EXECUTE ON FUNCTION extensions.pgrst_ddl_watch() FROM PUBLIC",
            "ALTER FUNCTION pg_catalog.pg_event_trigger_ddl_commands() COST 11",
            "ALTER FUNCTION pg_catalog.pg_event_trigger_dropped_objects() SET dxos.fixture_token='fixture_value'",
            "REVOKE EXECUTE ON FUNCTION pg_catalog.pg_event_trigger_ddl_commands() FROM PUBLIC",
            "ALTER ROLE supabase_admin NOSUPERUSER",
            "GRANT wf25_runtime TO supabase_admin",
            "SET LOCAL session_replication_role='replica'",
            "SET LOCAL event_triggers=off"
    })
    void rejectsLiveTriggerFunctionHelperAclAndSessionDrift(String change) throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute(change.replace("wf25_runtime", WorkflowIsolationIntegrationTest.role()));
                }
                assertThatThrownBy(() -> guard.verify(connection, SCHEMA))
                        .isInstanceOfSatisfying(SQLException.class, failure ->
                                assertThat(failure.getSQLState()).isEqualTo("42501"))
                        .hasMessage("Workflow event-trigger guard rejected").hasNoCause();
            } finally {
                // Restore even built-in catalog/ACL fixture mutations before another test runs.
                connection.rollback();
            }
        }
    }

    @Test void rejectsUnknownDisabledTriggerToo() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE EVENT TRIGGER wf25_unknown ON ddl_command_end EXECUTE FUNCTION extensions.pgrst_ddl_watch()");
                    statement.execute("ALTER EVENT TRIGGER wf25_unknown DISABLE");
                }
                assertThatThrownBy(() -> guard.verify(connection, SCHEMA)).isInstanceOf(SQLException.class);
            } finally { connection.rollback(); }
        }
    }

    @Test void rejectsChangedTags() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP EVENT TRIGGER pgrst_ddl_watch");
                    statement.execute("CREATE EVENT TRIGGER pgrst_ddl_watch ON ddl_command_end WHEN TAG IN ('CREATE TABLE') EXECUTE FUNCTION extensions.pgrst_ddl_watch()");
                    statement.execute("ALTER EVENT TRIGGER pgrst_ddl_watch OWNER TO supabase_admin");
                }
                assertThatThrownBy(() -> guard.verify(connection, SCHEMA)).isInstanceOf(SQLException.class);
            } finally { connection.rollback(); }
        }
    }

    @Test void distinguishesUnrestrictedNullTagsFromEmptyTags() throws Exception {
        var changed = approved.deepCopy();
        changed.path("inventory").path("triggers").forEach(trigger -> {
            if (trigger.path("name").asText().equals("pgrst_ddl_watch")) ((ObjectNode) trigger).put("tagsUnrestricted", false);
        });
        try (Connection connection = connection()) {
            assertThatThrownBy(() -> new WorkflowEventTriggerGuard(changed).verify(connection, SCHEMA))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test void rejectsWrongSchemaAndServerVersion() throws Exception {
        try (Connection connection = connection()) {
            assertThatThrownBy(() -> guard.verify(connection, "other_private")).isInstanceOf(SQLException.class);
            var changed = approved.deepCopy().put("serverVersion", "0.0");
            assertThatThrownBy(() -> new WorkflowEventTriggerGuard(changed).verify(connection, SCHEMA))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test void strictReadOnlyGuardWorksBeforeSchemaProvisioningAndDoesNotBypassEmptyCatalog() throws Exception {
        execute("DROP SCHEMA " + SCHEMA);
        schemaCreated = false;
        try (Connection connection = connection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                guard.verifyReadOnly(connection, SCHEMA);
                assertThatThrownBy(() -> WorkflowEventTriggerGuard.REVIEWED.verifyReadOnly(connection, SCHEMA))
                        .isInstanceOf(SQLException.class).hasMessage("Workflow event-trigger guard rejected");
            } finally { connection.rollback(); }
        }
        for (String name : createdTriggers) execute("DROP EVENT TRIGGER " + name);
        createdTriggers.clear();
        try (Connection connection = connection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                assertThatThrownBy(() -> guard.verifyReadOnly(connection, SCHEMA)).isInstanceOf(SQLException.class);
            } finally { connection.rollback(); }
        }
    }

    @Test void strictReadOnlyGuardRejectsMissingHelperAndManifestDrift() throws Exception {
        try (Connection connection = connection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                var missing = approved.deepCopy();
                ((ObjectNode) missing.withArray("helperEvidence").get(0)).put("signature", "pg_catalog.wf25_missing_helper()");
                assertThatThrownBy(() -> new WorkflowEventTriggerGuard(missing).verifyReadOnly(connection, SCHEMA))
                        .isInstanceOf(SQLException.class).hasMessage("Workflow event-trigger guard rejected").hasNoCause();
            } finally { connection.rollback(); }
        }
        try (Connection connection = connection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                var drift = approved.deepCopy().put("serverVersion", "0.0");
                assertThatThrownBy(() -> new WorkflowEventTriggerGuard(drift).verifyReadOnly(connection, SCHEMA))
                        .isInstanceOf(SQLException.class);
            } finally { connection.rollback(); }
        }
    }

    @Test void strictReadOnlyGuardRejectsWritableTransaction() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                assertThatThrownBy(() -> guard.verifyReadOnly(connection, SCHEMA)).isInstanceOf(SQLException.class);
            } finally { connection.rollback(); }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_CA_CERT", matches = ".+")
    void liveRunnerUsesActualVerifiedTlsReadOnlyTransactionAndMeasuresCatalogChecks() throws Exception {
        for (String name : List.of("V3__create_workflow_persistence.sql", "V4__seed_workflow_definitions.sql")) {
            java.nio.file.Files.copy(java.nio.file.Path.of("src/main/resources/db/migration", name), bundle.resolve(name));
        }
        var environment = Map.of("DB_HOST", "aws-0-fixture.pooler.supabase.com", "DB_PORT", "5432",
                "DB_NAME", "workflow_test", "DB_USER", System.getenv("WORKFLOW_TEST_MIGRATION_USER"),
                "DB_PASSWORD", System.getenv("WORKFLOW_TEST_MIGRATION_PASSWORD"),
                "PGSSLROOTCERT", System.getenv("WORKFLOW_TEST_CA_CERT"));
        var output = new java.io.ByteArrayOutputStream();
        var errors = new java.io.ByteArrayOutputStream();
        var url = System.getenv("WORKFLOW_TEST_JDBC_URL").replace("127.0.0.1", "localhost");
        assertThat(WorkflowLiveGuard.run(environment, bundle,
                (ignoredCloudUrl, properties) -> java.sql.DriverManager.getConnection(url, properties),
                guard, new java.io.PrintStream(output), new java.io.PrintStream(errors)))
                .as(errors.toString()).isZero();
        assertThat(output.toString()).contains("LIVE_GUARD=PASS", "pooler_backend_ssl=TRUE", "catalog_guard_ms samples=5");
        // Fixture timing is evidence about local catalog queries, never Supabase acceptance.
        System.out.println("LOCAL_SYNTHETIC_FIXTURE " + output.toString().lines()
                .filter(line -> line.startsWith("catalog_guard_ms ")).findFirst().orElseThrow());
    }

    @ParameterizedTest @ValueSource(strings = {"hostname", "ca"})
    @EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_WRONG_CA_CERT", matches = ".+")
    void liveRunnerRejectsActualInvalidTls(String mismatch) throws Exception {
        for (String name : List.of("V3__create_workflow_persistence.sql", "V4__seed_workflow_definitions.sql")) {
            java.nio.file.Files.copy(java.nio.file.Path.of("src/main/resources/db/migration", name), bundle.resolve(name));
        }
        var environment = Map.of("DB_HOST", "aws-0-fixture.pooler.supabase.com", "DB_PORT", "5432",
                "DB_NAME", "workflow_test", "DB_USER", System.getenv("WORKFLOW_TEST_MIGRATION_USER"),
                "DB_PASSWORD", System.getenv("WORKFLOW_TEST_MIGRATION_PASSWORD"), "PGSSLROOTCERT",
                System.getenv(mismatch.equals("ca") ? "WORKFLOW_TEST_WRONG_CA_CERT" : "WORKFLOW_TEST_CA_CERT"));
        var output = new java.io.ByteArrayOutputStream();
        var errors = new java.io.ByteArrayOutputStream();
        String url = System.getenv("WORKFLOW_TEST_JDBC_URL");
        if (mismatch.equals("ca")) url = url.replace("127.0.0.1", "localhost");
        final String localUrl = url;
        assertThat(WorkflowLiveGuard.run(environment, bundle,
                (ignoredCloudUrl, properties) -> java.sql.DriverManager.getConnection(localUrl, properties),
                guard, new java.io.PrintStream(output), new java.io.PrintStream(errors))).isEqualTo(1);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString().trim()).isEqualTo("LIVE_GUARD=FAIL phase=connect_tls");
    }

    private Connection connection() throws SQLException {
        return WorkflowIsolationIntegrationTest.source(SCHEMA, true, null).getConnection();
    }

    private void execute(String sql) throws SQLException {
        try (var connection = WorkflowIsolationIntegrationTest.source(null, true, null).getConnection();
             var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
