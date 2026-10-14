package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class WorkflowDevelopmentSmokeRunnerTest {
    @TempDir Path temporary;
    Map<String, String> environment;

    @BeforeEach void setup() throws Exception {
        Path bundle = Files.createDirectory(temporary.resolve("bundle"));
        for (String name : new String[]{"V3__create_workflow_persistence.sql", "V4__seed_workflow_definitions.sql"}) {
            Files.copy(Path.of("src/main/resources/db/migration", name), bundle.resolve(name));
        }
        Path ca = Files.writeString(temporary.resolve("ca.pem"), "unit fixture only");
        environment = new HashMap<>(Map.of(
                "WORKFLOW_DEV_INTEGRATION_OPT_IN", WorkflowDevelopmentSmokeRunner.OPT_IN,
                "WORKFLOW_DEV_APPROVED_SCHEMA", WorkflowSmokeRunner.SCHEMA,
                "WORKFLOW_DEV_BUNDLE", bundle.toString(),
                "WORKFLOW_DEV_PROJECT_REF", "abcdefghijklmnopqrst",
                "DB_HOST", "db.abcdefghijklmnopqrst.supabase.co",
                "DB_PORT", "5432", "DB_NAME", "postgres", "DB_USER", "postgres",
                "DB_PASSWORD", "secret-do-not-log", "PGSSLROOTCERT", ca.toString()));
    }

    @Test void readOnlyPreparationDoesNotConnectOrRequireWriteApproval() {
        assertThatCode(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "check")).doesNotThrowAnyException();
        assertThatCode(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "read")).doesNotThrowAnyException();
        assertThatCode(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "audit")).doesNotThrowAnyException();
        var runner = WorkflowDevelopmentSmokeRunner.prepare(environment, "check");
        assertThatThrownBy(runner::migrate).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Development write approval required");
        assertThatThrownBy(() -> runner.smoke("not-approved")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Development write approval required");
    }

    @ParameterizedTest @ValueSource(strings = {"migrate", "smoke"})
    void writeModesRequireSeparateSchemaApproval(String mode) {
        assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, mode))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Explicit write approval required");
        environment.put("WORKFLOW_DEV_WRITE_APPROVAL", WorkflowSmokeRunner.SCHEMA);
        assertThatCode(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, mode)).doesNotThrowAnyException();
    }

    @ParameterizedTest @ValueSource(strings = {"", "true", "false", "production", "ISSUE-25-POSTGRES-DEVELOPMENT-ONLY"})
    void weakOrAccidentalOptInsAreRejected(String value) {
        environment.put("WORKFLOW_DEV_INTEGRATION_OPT_IN", value);
        assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "check"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Development opt-in required");
    }

    @ParameterizedTest @ValueSource(strings = {"public", "storage", "auth", "dxos_wf25_smoke_02", "dxos_wf25_smoke_01;DROP SCHEMA public"})
    void noOtherSchemaCanBeApproved(String schema) {
        environment.put("WORKFLOW_DEV_APPROVED_SCHEMA", schema);
        assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "check"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Approved schema required");
    }

    @ParameterizedTest @ValueSource(strings = {
            "aws-0-region.pooler.supabase.com", "127.0.0.1", "other.example.com",
            "db.abcdefghijklmnopqrst.supabase.co?sslmode=disable", "db.abcdefghijklmnopqrst.supabase.co.evil.example"})
    void endpointOverridesAndLocalWithoutDisposableOptInAreRejected(String host) {
        environment.put("DB_HOST", host);
        assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "check"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Direct endpoint required");
    }

    @Test void projectPortDatabaseUserCaAndBundleRemainRequired() {
        for (var override : Map.of("WORKFLOW_DEV_PROJECT_REF", "differentprojectref", "DB_PORT", "6543",
                "DB_NAME", "other", "DB_USER", "dxos_wf25_runtime", "PGSSLROOTCERT", "missing-ca",
                "WORKFLOW_DEV_BUNDLE", "missing-bundle").entrySet()) {
            var changed = new HashMap<>(environment);
            changed.put(override.getKey(), override.getValue());
            assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(changed, "check"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"WORKFLOW_DEV_LOCAL_DISPOSABLE", "WF25_DISPOSABLE_TEST", "WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE"})
    void localFlagsCannotBeMixedIntoCloudConfiguration(String name) {
        environment.put(name, "false");
        assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "check"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Disposable profile rejected");
    }

    @Test void productionConfigurationAndMissingCommandCannotOptIn() {
        var production = new HashMap<>(environment);
        production.remove("WORKFLOW_DEV_INTEGRATION_OPT_IN");
        production.put("SPRING_PROFILES_ACTIVE", "development");
        production.put("WORKFLOW_SMOKE_LOCAL_DISPOSABLE", "true");
        assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(production, "check"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Development opt-in required");
        for (String[] args : new String[][]{{}, {"check"}, {"migrate"}, {"--development-integration", "check", "unexpected"}}) {
            var output = new ByteArrayOutputStream();
            var errors = new ByteArrayOutputStream();
            assertThat(WorkflowDevelopmentSmokeRunner.run(environment, args, new PrintStream(output), new PrintStream(errors)))
                    .isEqualTo(1);
            assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
            assertThat(errors.toString(StandardCharsets.UTF_8).trim()).isEqualTo("WORKFLOW_DEVELOPMENT_SMOKE_FAIL");
        }
    }

    @Test void disposableLocalOptInCannotDowngradeOrReachCloud() {
        environment.put("WORKFLOW_DEV_LOCAL_DISPOSABLE", "true");
        environment.put("WF25_DISPOSABLE_TEST", "1");
        environment.put("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE", "true");
        assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "check"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Direct endpoint required");
    }
}
