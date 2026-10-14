package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** DDL fixtures are confined to a disposable loopback database; capture itself remains read-only. */
@EnabledIfEnvironmentVariable(named = "WORKFLOW_AUDIT_LOCAL_JDBC_URL", matches = ".+")
class WorkflowSecurityDefinerReviewIntegrationTest {
    @TempDir Path temporary;
    private String schema;
    private boolean runtimeCreated;

    @BeforeEach void fixture() throws Exception {
        schema = "wf25_sd_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("GRANT USAGE ON SCHEMA " + schema + " TO PUBLIC");
        execute("CREATE FUNCTION " + schema + ".helper() RETURNS integer LANGUAGE sql AS 'SELECT 1'");
        execute("CREATE FUNCTION " + schema + ".demo() RETURNS integer LANGUAGE plpgsql SECURITY DEFINER "
                + "SET search_path=pg_catalog,pg_temp SET app.review_token='fixture-config-secret' AS $$ "
                + "BEGIN /* fixture-body-secret */ RETURN " + schema + ".helper(); END $$");
        execute("CREATE TABLE " + schema + ".guarded(id integer)");
        execute("CREATE FUNCTION " + schema + ".guard() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER "
                + "SET search_path=pg_catalog,pg_temp AS $$ BEGIN RETURN NEW; END $$");
        execute("CREATE TRIGGER guard BEFORE INSERT ON " + schema + ".guarded FOR EACH ROW EXECUTE FUNCTION " + schema + ".guard()");
    }

    @AfterEach void cleanup() throws Exception {
        if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE");
        if (runtimeCreated) execute("DROP ROLE dxos_wf25_runtime");
    }

    @Test void actualMetadataIdentifiesDefinersAndPotentialPublicAccessWithoutBodies() throws Exception {
        Path directory = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary);
        var result = capture(environment(directory), true);
        assertThat(result.exit()).as(result.errors()).isZero();
        assertThat(result.output()).contains("SECURITY_DEFINER_CAPTURE=PASS", "WRITE_READINESS=BLOCKED");
        String shared = Files.readString(directory.resolve("shareable-metadata.json"));
        assertThat(shared).doesNotContain("fixture-body-secret", "fixture-config-secret", "fixture-only-password");
        JsonNode metadata = new ObjectMapper().readTree(shared);
        JsonNode demo = routine(metadata, "demo");
        assertThat(demo.path("owner").asText()).isEqualTo("postgres");
        assertThat(demo.path("runtime_execute").isNull()).isTrue();
        assertThat(demo.path("runtime_exists").asBoolean()).isFalse();
        assertThat(demo.path("public_execute").asBoolean()).isTrue();
        assertThat(demo.path("public_schema_usage").asBoolean()).isTrue();
        assertThat(demo.path("function_search_path").asText()).isEqualTo("pg_catalog, pg_temp");
        assertThat(routine(metadata, "guard").path("call_context").asText()).isEqualTo("TRIGGER_CONTEXT_ONLY");
        assertThat(metadata.path("catalog").path("session").get(0).path("backend_ssl").asBoolean()).isTrue();
        assertThat(metadata.path("catalog").path("dependencies").toString()).contains("pg_trigger", "incoming");
        assertThat(Files.exists(directory.resolve("private-function-review.json"))).isFalse();
    }

    @Test void validBaselineAllowsProtectedPrivateCaptureAndBoundManualHelpers() throws Exception {
        Path first = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary);
        var environment = environment(first);
        environment.put("WORKFLOW_DEFINER_HELPER_SIGNATURES", schema + ".helper()\npg_catalog.row_number()");
        assertThat(capture(environment, true).exit()).isZero();
        Path second = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary);
        environment = environment(second);
        environment.put("WORKFLOW_DEFINER_METADATA_FILE", first.resolve("shareable-metadata.json").toString());
        var result = capture(environment, true);
        assertThat(result.exit()).as(result.errors()).isZero();
        String shared = Files.readString(second.resolve("shareable-metadata.json"));
        String privateReview = Files.readString(second.resolve("private-function-review.json"));
        assertThat(privateReview).contains("fixture-body-secret", "fixture-config-secret", schema + ".helper");
        assertThat(shared + result.output() + result.errors()).doesNotContain("fixture-body-secret", "fixture-config-secret", "fixture-only-password");
        assertThat(shared).contains("COMPLETE_BEHAVIOR_NOT_REVIEWED", "NEEDS_REVIEW", "CATALOG_ONLY_NOT_COMPLETE");
        WorkflowEventTriggerReview.verifyDirectory(second, false);
    }

    @Test void changedBodyBlocksPrivateCapture() throws Exception {
        Path first = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary);
        assertThat(capture(environment(first), true).exit()).isZero();
        execute("CREATE OR REPLACE FUNCTION " + schema + ".demo() RETURNS integer LANGUAGE sql SECURITY DEFINER "
                + "SET search_path=pg_catalog,pg_temp AS 'SELECT 2'");
        Path second = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary);
        var environment = environment(second);
        environment.put("WORKFLOW_DEFINER_METADATA_FILE", first.resolve("shareable-metadata.json").toString());
        var result = capture(environment, true);
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.errors().trim()).isEqualTo("SECURITY_DEFINER_CAPTURE=FAIL phase=baseline");
        assertThat(result.output()).isEmpty();
        assertThat(second.resolve("private-function-review.json")).doesNotExist();
    }

    @Test void falseExecuteIsOnlyReportedWhenRuntimeActuallyExists() throws Exception {
        execute("CREATE ROLE dxos_wf25_runtime NOLOGIN");
        runtimeCreated = true;
        execute("GRANT USAGE ON SCHEMA " + schema + " TO dxos_wf25_runtime");
        execute("REVOKE EXECUTE ON FUNCTION " + schema + ".demo() FROM PUBLIC");
        Path directory = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary);
        var result = capture(environment(directory), true);
        assertThat(result.exit()).as(result.errors()).isZero();
        JsonNode demo = routine(new ObjectMapper().readTree(Files.readString(directory.resolve("shareable-metadata.json"))), "demo");
        assertThat(demo.path("runtime_exists").asBoolean()).isTrue();
        assertThat(demo.path("runtime_execute").isBoolean()).isTrue();
        assertThat(demo.path("runtime_execute").asBoolean()).isFalse();
        assertThat(demo.path("runtime_schema_usage").asBoolean()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"ca", "hostname"})
    void actualVerifyFullRejectsWrongCaAndHostname(String mismatch) throws Exception {
        Path directory = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary);
        var environment = environment(directory);
        if (mismatch.equals("ca")) environment.put("PGSSLROOTCERT", required("WORKFLOW_AUDIT_LOCAL_WRONG_CA_CERT"));
        var result = capture(environment, !mismatch.equals("hostname"));
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.output()).isEmpty();
        assertThat(result.errors().trim()).isEqualTo("SECURITY_DEFINER_CAPTURE=FAIL phase=connect_tls");
        assertThat(directory.resolve("shareable-metadata.json")).doesNotExist();
    }

    @Test void malformedHelperIsBoundNeverExecutedOrLogged() throws Exception {
        Path directory = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary);
        var environment = environment(directory);
        environment.put("WORKFLOW_DEFINER_HELPER_SIGNATURES", schema + ".helper()'); SELECT 'fixture-injection-secret'; --");
        var result = capture(environment, true);
        assertThat(result.exit()).isEqualTo(1);
        assertThat(result.errors().trim()).isEqualTo("SECURITY_DEFINER_CAPTURE=FAIL phase=helper_selection");
        assertThat(result.output() + result.errors()).doesNotContain("fixture-injection-secret");
        assertThat(directory.resolve("shareable-metadata.json")).doesNotExist();
    }

    private record Result(int exit, String output, String errors) {}

    private Result capture(Map<String, String> environment, boolean validHostname) {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int exit = WorkflowSecurityDefinerReview.run(environment, WorkflowSecurityDefinerReviewTest.SQL,
                (ignored, properties) -> DriverManager.getConnection(validHostname ? localUrl().replace("127.0.0.1", "localhost") : localUrl(), properties),
                new PrintStream(output), new PrintStream(errors));
        return new Result(exit, output.toString(), errors.toString());
    }

    private JsonNode routine(JsonNode metadata, String name) {
        for (JsonNode routine : metadata.path("catalog").path("routines"))
            if (routine.path("schema_name").asText().equals(schema) && routine.path("routine_name").asText().equals(name)) return routine;
        throw new AssertionError("Fixture routine missing");
    }

    private Map<String, String> environment(Path directory) {
        return new HashMap<>(Map.of("DB_HOST", "aws-0-fixture.pooler.supabase.com", "DB_PORT", "5432", "DB_NAME", "postgres",
                "DB_USER", "postgres", "DB_PASSWORD", required("WORKFLOW_AUDIT_LOCAL_DB_PASSWORD"), "PGSSLROOTCERT", required("WORKFLOW_AUDIT_LOCAL_CA_CERT"),
                "WORKFLOW_DEFINER_REVIEW_DIR", directory.toString()));
    }

    private void execute(String sql) throws Exception {
        localUrl();
        var properties = WorkflowLiveGuard.connectionProperties(environment(temporary));
        properties.setProperty("currentSchema", "pg_catalog,pg_temp");
        properties.remove("options");
        properties.setProperty("readOnly", "false");
        try (var connection = DriverManager.getConnection(localUrl().replace("127.0.0.1", "localhost"), properties);
             var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private String localUrl() {
        String url = required("WORKFLOW_AUDIT_LOCAL_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/postgres")) throw new IllegalArgumentException("Disposable loopback fixture required");
        return url;
    }

    private String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing local TLS fixture");
        return value;
    }
}
