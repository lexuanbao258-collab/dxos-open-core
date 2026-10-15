package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_CA_CERT", matches = ".+")
class WorkflowEventTriggerReviewIntegrationTest {
    @TempDir Path temporary;
    final Map<String, String> events = new LinkedHashMap<>(Map.of(
            "pgrst_ddl_watch", "ddl_command_end",
            "pgrst_drop_watch", "sql_drop",
            "issue_pg_cron_access", "ddl_command_end",
            "issue_pg_graphql_access", "ddl_command_end",
            "issue_pg_net_access", "ddl_command_end",
            "issue_graphql_placeholder", "sql_drop"));
    boolean roleCreated, schemaCreated;

    @BeforeEach void fixture() throws Exception {
        // This shared-catalog fixture is limited by source() to a disposable loopback database.
        execute("CREATE ROLE supabase_admin SUPERUSER NOLOGIN");
        roleCreated = true;
        execute("CREATE SCHEMA extensions");
        schemaCreated = true;
        execute("CREATE FUNCTION extensions.wf25_review_helper() RETURNS integer LANGUAGE plpgsql AS $$ BEGIN /* fixture_helper_secret */ RETURN 1; END $$");
        for (var event : events.entrySet()) {
            execute("CREATE FUNCTION extensions." + event.getKey() + "() RETURNS event_trigger LANGUAGE plpgsql SECURITY DEFINER "
                    + "SET search_path=pg_catalog,extensions SET app.review_token='fixture_config_secret' "
                    + "AS $$ BEGIN /* fixture_body_secret */ PERFORM extensions.wf25_review_helper(); END $$");
            execute("ALTER FUNCTION extensions." + event.getKey() + "() OWNER TO supabase_admin");
        }
        for (var event : events.entrySet()) {
            String tag = event.getKey().startsWith("pgrst_") ? ""
                    : " WHEN TAG IN ('" + (event.getValue().equals("sql_drop") ? "DROP EXTENSION" : "CREATE EXTENSION") + "')";
            execute("CREATE EVENT TRIGGER " + event.getKey() + " ON " + event.getValue() + tag
                    + " EXECUTE FUNCTION extensions." + event.getKey() + "()");
            execute("ALTER EVENT TRIGGER " + event.getKey() + " OWNER TO supabase_admin");
        }
    }

    @AfterEach void cleanup() throws Exception {
        if (schemaCreated) {
            for (String name : events.keySet()) execute("DROP EVENT TRIGGER IF EXISTS " + name);
            execute("DROP SCHEMA extensions CASCADE");
        }
        if (roleCreated) execute("DROP ROLE supabase_admin");
    }

    @Test void capturesConfirmedMetadataWithoutPublishingBodiesOrConfigurationValues() throws Exception {
        Path directory = protectedDirectory();
        var environment = environment(directory);
        environment.put("WORKFLOW_EVENT_REVIEW_HELPER_OIDS", Long.toString(helperOid()));
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int exit = WorkflowEventTriggerReview.run(environment, (ignoredUrl, properties) -> verifiedConnection(properties, false),
                new PrintStream(output), new PrintStream(errors));
        assertThat(exit).as(errors.toString()).isZero();
        String shared = Files.readString(directory.resolve("shareable-metadata.json"));
        String privateReview = Files.readString(directory.resolve("private-function-review.json"));
        var metadata = new ObjectMapper().readTree(shared);
        assertThat(metadata.path("inventoryMatchesConfirmedMetadata").asBoolean()).isTrue();
        assertThat(metadata.path("triggers").size()).isEqualTo(6);
        assertThat(metadata.path("guardReviewStatus").asText()).isEqualTo("BLOCKED");
        assertThat(metadata.path("snapshotSha256").asText()).matches("[a-f0-9]{64}");
        assertThat(metadata.path("session").path("transactionReadOnly").asText()).isEqualTo("on");
        assertThat(metadata.path("functions").size()).isEqualTo(7);
        assertThat(metadata.path("functions").get(0).has("ownerCreateRole")).isTrue();
        assertThat(metadata.path("functions").get(0).has("ownerMemberships")).isTrue();
        assertThat(shared + output + errors).doesNotContain("fixture_body_secret", "fixture_config_secret", "fixture_helper_secret");
        assertThat(privateReview).contains("fixture_body_secret", "fixture_config_secret", "fixture_helper_secret");
    }

    @Test void matchingNamesAndMetadataDoNotAuthorizeChangedDeployedBodies() throws Exception {
        Path first = protectedDirectory();
        assertThat(capture(first, false)).isZero();
        var before = new ObjectMapper().readTree(Files.readString(first.resolve("shareable-metadata.json")));
        execute("CREATE OR REPLACE FUNCTION extensions.pgrst_ddl_watch() RETURNS event_trigger LANGUAGE plpgsql "
                + "SECURITY DEFINER SET search_path=pg_catalog,extensions SET app.review_token='fixture_config_secret' "
                + "AS $$ BEGIN /* changed_private_body */ PERFORM extensions.wf25_review_helper(); END $$");
        Path second = protectedDirectory();
        assertThat(capture(second, false)).isZero();
        var after = new ObjectMapper().readTree(Files.readString(second.resolve("shareable-metadata.json")));
        assertThat(after.path("inventoryMatchesConfirmedMetadata").asBoolean()).isTrue();
        assertThat(after.path("snapshotSha256").asText()).isNotEqualTo(before.path("snapshotSha256").asText());
        assertThat(after.path("guardReviewStatus").asText()).isEqualTo("BLOCKED");
        assertThat(after.toString()).doesNotContain("changed_private_body", "fixture_config_secret");
    }

    @Test void collectsHelpersIdentifiedDuringPrivateBodyReviewByBoundSignature() throws Exception {
        Path directory = protectedDirectory();
        var environment = environment(directory);
        environment.put("WORKFLOW_EVENT_REVIEW_HELPER_SIGNATURES", "extensions.wf25_review_helper()");
        int exit = WorkflowEventTriggerReview.run(environment, (url, properties) -> verifiedConnection(properties, false),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
        assertThat(exit).isZero();
        assertThat(Files.readString(directory.resolve("private-function-review.json"))).contains("fixture_helper_secret");
        assertThat(Files.readString(directory.resolve("shareable-metadata.json"))).doesNotContain("fixture_helper_secret");
    }

    @Test void capturesUnknownEnabledTriggersWithoutSilentlyFilteringThem() throws Exception {
        execute("CREATE EVENT TRIGGER wf25_unreviewed ON ddl_command_end EXECUTE FUNCTION extensions.pgrst_ddl_watch()");
        try {
            Path directory = protectedDirectory();
            assertThat(capture(directory, false)).isZero();
            var metadata = new ObjectMapper().readTree(Files.readString(directory.resolve("shareable-metadata.json")));
            assertThat(metadata.path("triggers").size()).isEqualTo(7);
            assertThat(metadata.path("inventoryMatchesConfirmedMetadata").asBoolean()).isFalse();
            assertThat(metadata.path("guardReviewStatus").asText()).isEqualTo("BLOCKED");
        } finally { execute("DROP EVENT TRIGGER wf25_unreviewed"); }
    }

    @Test void rollbackFailureDoesNotPersistSourceOrLeakExceptionText() throws Exception {
        Path directory = protectedDirectory();
        var errors = new ByteArrayOutputStream();
        int exit = WorkflowEventTriggerReview.run(environment(directory), (url, properties) -> verifiedConnection(properties, true),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors));
        assertThat(exit).isEqualTo(1);
        assertThat(errors.toString().trim()).isEqualTo("JDBC_PREFLIGHT_FAIL phase=rollback sqlstate=08006");
        try (var files = Files.list(directory)) { assertThat(files.toList()).isEmpty(); }
    }

    @Test void broadDirectoryAclStopsBeforeConnecting() throws Exception {
        Path directory = protectedDirectory();
        Process process = new ProcessBuilder("icacls.exe", directory.toString(), "/grant", "*S-1-1-0:(OI)(CI)RX")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor()).isZero();
        var errors = new ByteArrayOutputStream();
        int exit = WorkflowEventTriggerReview.run(environment(directory), (url, properties) -> {
            throw new AssertionError("Unsafe output must be rejected before JDBC");
        }, new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors));
        assertThat(exit).isEqualTo(1);
        assertThat(errors.toString().trim()).isEqualTo("EVENT_REVIEW_FAIL phase=validation error=unsafe_output_or_helpers");
        try (var files = Files.list(directory)) { assertThat(files.toList()).isEmpty(); }
    }

    @Test void malformedHelperSignatureIsBoundAndNotLoggedOrExecuted() throws Exception {
        Path directory = protectedDirectory();
        var environment = environment(directory);
        environment.put("WORKFLOW_EVENT_REVIEW_HELPER_SIGNATURES", "extensions.wf25_review_helper()'); SELECT 'fixture_injection_secret'; --");
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int exit = WorkflowEventTriggerReview.run(environment, (url, properties) -> verifiedConnection(properties, false),
                new PrintStream(output), new PrintStream(errors));
        assertThat(exit).isEqualTo(1);
        assertThat(errors.toString().trim()).matches("JDBC_PREFLIGHT_FAIL phase=event_review sqlstate=(42601|42704)");
        assertThat(output + errors.toString()).doesNotContain("fixture_injection_secret");
        try (var files = Files.list(directory)) { assertThat(files.toList()).isEmpty(); }
    }

    @Test void readOnlyReferenceSqlExecutesAgainstAllSixActualMetadataShapes() throws Exception {
        String sql = Files.readString(Path.of("../docs/evidence/issue-25-event-trigger-catalog-review.sql"));
        try (var connection = WorkflowIsolationIntegrationTest.source(null, true, null).getConnection();
             var statement = connection.createStatement()) {
            boolean result = statement.execute(sql);
            int matched = 0;
            while (result || statement.getUpdateCount()!=-1) {
                if (result) {
                    try (var rows=statement.getResultSet()) {
                        var columns=rows.getMetaData();
                        boolean inventory=false;
                        for (int column=1;column<=columns.getColumnCount();column++)
                            inventory |= columns.getColumnLabel(column).equals("matches_confirmed_metadata");
                        if (inventory) while(rows.next()) {
                            assertThat(rows.getBoolean("matches_confirmed_metadata")).isTrue(); matched++;
                        }
                    }
                }
                result=statement.getMoreResults();
            }
            assertThat(matched).isEqualTo(6);
        }
    }

    private int capture(Path directory, boolean failRollback) {
        return WorkflowEventTriggerReview.run(environment(directory), (url, properties) -> verifiedConnection(properties, failRollback),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
    }

    private Connection verifiedConnection(Properties properties, boolean failRollback) throws SQLException {
        Connection actual = DriverManager.getConnection(System.getenv("WORKFLOW_TEST_JDBC_URL").replace("127.0.0.1", "localhost"), properties);
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createStatement") || method.getName().equals("prepareStatement")) {
                        assertThat(actual.getAutoCommit()).isFalse();
                        try (var statement = actual.createStatement(); var rows = statement.executeQuery("SELECT pg_catalog.current_setting('transaction_read_only')")) {
                            assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("on");
                        }
                    }
                    if (failRollback && method.getName().equals("rollback")) {
                        actual.rollback(); throw new SQLException("fixture_body_secret", "08006");
                    }
                    try { return method.invoke(actual, args); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
    }

    private Map<String, String> environment(Path directory) {
        return new HashMap<>(Map.of("DB_HOST", "local-fixture.pooler.supabase.com", "DB_PORT", "5432",
                "DB_NAME", "workflow_test", "DB_USER", System.getenv("WORKFLOW_TEST_MIGRATION_USER"),
                "DB_PASSWORD", System.getenv("WORKFLOW_TEST_MIGRATION_PASSWORD"), "PGSSLROOTCERT", System.getenv("WORKFLOW_TEST_CA_CERT"),
                "WORKFLOW_EVENT_REVIEW_DIR", directory.toString()));
    }

    private Path protectedDirectory() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve(UUID.randomUUID().toString()));
        String user = System.getenv("USERDOMAIN") + "\\" + System.getenv("USERNAME");
        Files.setOwner(directory, directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(user));
        Process process = new ProcessBuilder("icacls.exe", directory.toString(), "/inheritance:r", "/grant:r",
                user + ":(OI)(CI)F", "/grant:r", "*S-1-5-18:(OI)(CI)F")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor()).isZero();
        return directory;
    }

    private long helperOid() throws SQLException {
        try (var connection = WorkflowIsolationIntegrationTest.source(null, true, null).getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT 'extensions.wf25_review_helper()'::pg_catalog.regprocedure::oid")) {
            rows.next(); return rows.getLong(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var connection = WorkflowIsolationIntegrationTest.source(null, true, null).getConnection();
             var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
