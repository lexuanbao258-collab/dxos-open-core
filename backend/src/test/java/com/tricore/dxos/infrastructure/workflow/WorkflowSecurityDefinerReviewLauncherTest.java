package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WorkflowSecurityDefinerReviewLauncherTest {
    @TempDir Path temporary;
    private static final String SUCCESS = "echo {} > \"%WORKFLOW_DEFINER_REVIEW_DIR%\\shareable-metadata.json\"\r\n"
            + "echo SECURITY_DEFINER_CAPTURE=PASS scope=READ_ONLY_CATALOG client_tls=VERIFIED_VERIFY_FULL\r\n"
            + "echo BEHAVIOR_REVIEW=NEEDS_REVIEW; WRITE_READINESS=BLOCKED\r\n";

    @Test void nativeOutputIsFilteredAndCredentialsStayOutOfArguments() throws Exception {
        String output = launch("@echo off\r\necho private-body-secret password=fixture-password\r\n"
                + "echo %* > arguments.txt\r\necho %WORKFLOW_DEFINER_METADATA_FILE% > metadata-env.txt\r\n"
                + SUCCESS + "exit /b 0\r\n", null, 0);
        assertThat(output).contains("SHAREABLE_METADATA=", "READY_FOR_RUNTIME_WRITE=NO", "NEEDS_REVIEW")
                .doesNotContain("fixture-password", "private-body-secret", "PRIVATE_LOCAL_REVIEW=", "FAIL");
        assertThat(Files.readString(temporary.resolve("backend/arguments.txt"))).contains("workflowSecurityDefinerReview")
                .doesNotContain("fixture-password", "DB_PASSWORD", "fixture-ref");
        assertThat(Files.readString(temporary.resolve("backend/metadata-env.txt"))).doesNotContain("inherited-private-mode");
    }

    @ParameterizedTest @ValueSource(strings = {"@echo off\r\nexit /b 0\r\n",
            "@echo off\r\necho SECURITY_DEFINER_CAPTURE=PASS scope=READ_ONLY_CATALOG client_tls=VERIFIED_VERIFY_FULL\r\nexit /b 0\r\n",
            "@echo off\r\necho SECURITY_DEFINER_CAPTURE=FAIL phase=connect_tls\r\necho fixture-password 1>&2\r\nexit /b 1\r\n"})
    void missingReceiptOrFailureNeverPublishesSuccess(String batch) throws Exception {
        assertThat(launch(batch, null, 1)).contains("SECURITY_DEFINER_CAPTURE=FAIL")
                .doesNotContain("SECURITY_DEFINER_CAPTURE=PASS", "SHAREABLE_METADATA=", "fixture-password");
    }

    @Test void nativeFailureAfterSuccessMarkerIsStillFailure() throws Exception {
        assertThat(launch("@echo off\r\n" + SUCCESS + "exit /b 1\r\n", null, 1))
                .contains("FAIL").doesNotContain("SECURITY_DEFINER_CAPTURE=PASS", "SHAREABLE_METADATA=");
    }

    @Test void privateModeUsesEnvironmentAndNeverPrintsBodies() throws Exception {
        Path metadata = Files.writeString(temporary.resolve("shareable-metadata.json"), "{}");
        String output = launch("@echo off\r\necho fixture-private-body 1>&2\r\n"
                + "echo fixture-private-body > \"%WORKFLOW_DEFINER_REVIEW_DIR%\\private-function-review.json\"\r\n"
                + "echo %WORKFLOW_DEFINER_METADATA_FILE% > baseline-env.txt\r\n"
                + "echo %WORKFLOW_DEFINER_HELPER_SIGNATURES% > helper-env.txt\r\n" + SUCCESS + "exit /b 0\r\n",
                null, 0, "-MetadataFile", metadata.toString(), "-HelperSignature", "public.helper(integer)");
        assertThat(output).contains("PRIVATE_LOCAL_REVIEW=", "SHAREABLE_METADATA=").doesNotContain("fixture-private-body");
        assertThat(Files.readString(temporary.resolve("backend/baseline-env.txt"))).contains(metadata.toString());
        assertThat(Files.readString(temporary.resolve("backend/helper-env.txt"))).contains("public.helper(integer)");
    }

    @ParameterizedTest @ValueSource(strings = {"DB_PASSWORD", "WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE"})
    void invalidEnvironmentNeverStartsGradle(String key) throws Exception {
        assertThat(launch("@echo off\r\necho invoked > invoked.txt\r\nexit /b 0\r\n", key, 1)).contains("phase=launcher");
        assertThat(temporary.resolve("backend/invoked.txt")).doesNotExist();
    }

    private String launch(String batch, String invalidKey, int expectedExit, String... options) throws Exception {
        Path scripts = Files.createDirectories(temporary.resolve("scripts"));
        Path backend = Files.createDirectories(temporary.resolve("backend"));
        Path launcher = scripts.resolve("workflow-security-definer-review.ps1");
        Files.copy(Path.of("../scripts/workflow-security-definer-review.ps1"), launcher);
        Files.writeString(backend.resolve("gradlew.bat"), batch);
        Path output = temporary.resolve("output.txt");
        var arguments = new ArrayList<>(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-File", launcher.toString()));
        arguments.addAll(Arrays.asList(options));
        var process = new ProcessBuilder(arguments)
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
        process.environment().putAll(Map.of("DB_HOST", "aws-0-fixture.pooler.supabase.com", "DB_PORT", "5432",
                "DB_NAME", "postgres", "DB_USER", "postgres.fixture-ref", "DB_PASSWORD", "fixture-password",
                "PGSSLROOTCERT", "fixture.pem", "LOCALAPPDATA", temporary.resolve("local-data").toString(),
                "WORKFLOW_DEFINER_METADATA_FILE", "inherited-private-mode"));
        process.environment().remove("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE");
        // Windows PowerShell must not inherit PowerShell 7's incompatible core modules.
        process.environment().remove("PSModulePath");
        if (invalidKey != null) {
            if (invalidKey.equals("DB_PASSWORD")) process.environment().remove(invalidKey);
            else process.environment().put(invalidKey, "false");
        }
        var running = process.start();
        if (!running.waitFor(30, TimeUnit.SECONDS)) {
            running.destroyForcibly();
            throw new AssertionError("Launcher timed out");
        }
        assertThat(running.exitValue()).withFailMessage(Files.readString(output)).isEqualTo(expectedExit);
        return Files.readString(output);
    }
}
