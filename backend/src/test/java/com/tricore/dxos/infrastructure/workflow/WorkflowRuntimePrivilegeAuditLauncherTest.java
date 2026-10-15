package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WorkflowRuntimePrivilegeAuditLauncherTest {
    @TempDir Path temporary;

    @Test void filtersNativeOutputAndKeepsSecretsOutOfArguments() throws Exception {
        String result = launch("@echo off\r\necho password=private-secret jdbc:postgresql://private-secret\r\n"
                + "echo %* > arguments.txt\r\necho ARGS=%*\r\necho RUNTIME_PRIVILEGE_AUDIT=PASS scope=READ_ONLY_CATALOG\r\n"
                + "echo AUDIT_REPORT={\"public_temp\":true}\r\necho WRITE_READINESS=BLOCKED\r\nexit /b 0\r\n", null, 0);
        assertThat(result).contains("RUNTIME_PRIVILEGE_AUDIT=PASS", "AUDIT_REPORT=", "WRITE_READINESS=BLOCKED")
                .doesNotContain("private-secret", "jdbc:postgresql:", "ARGS=", "FAIL");
        assertThat(Files.readString(temporary.resolve("backend/arguments.txt")))
                .contains("workflowRuntimePrivilegeAudit").doesNotContain("private-secret", "postgres.fixture-ref", "DB_PASSWORD");
    }

    @ParameterizedTest @ValueSource(strings = {"@echo off\r\necho private-secret\r\nexit /b 0\r\n",
            "@echo off\r\necho RUNTIME_PRIVILEGE_AUDIT=FAIL phase=connect_tls\r\necho private-secret 1>&2\r\nexit /b 1\r\n"})
    void missingResultOrProcessFailureReturnsFailureWithoutNativeErrors(String batch) throws Exception {
        assertThat(launch(batch, null, 1)).contains("RUNTIME_PRIVILEGE_AUDIT=FAIL").doesNotContain("private-secret");
    }

    @ParameterizedTest @ValueSource(strings = {"DB_PASSWORD", "WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE"})
    void invalidEnvironmentStopsBeforeGradle(String key) throws Exception {
        String batch = "@echo off\r\necho unexpected > invoked.txt\r\nexit /b 0\r\n";
        assertThat(launch(batch, key, 1)).contains("phase=launcher").doesNotContain("private-secret");
        assertThat(temporary.resolve("backend/invoked.txt")).doesNotExist();
    }

    private String launch(String batch, String invalidKey, int exit) throws Exception {
        Path scripts = Files.createDirectories(temporary.resolve("scripts"));
        Path backend = Files.createDirectories(temporary.resolve("backend"));
        Path launcher = scripts.resolve("workflow-runtime-privilege-audit.ps1");
        Files.copy(Path.of("../scripts/workflow-runtime-privilege-audit.ps1"), launcher);
        Files.writeString(backend.resolve("gradlew.bat"), batch);
        Path output = temporary.resolve("output.txt");
        var process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-File", launcher.toString())
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
        process.environment().putAll(Map.of("DB_HOST", "aws-0-fixture.pooler.supabase.com", "DB_PORT", "5432",
                "DB_NAME", "postgres", "DB_USER", "postgres.fixture-ref", "DB_PASSWORD", "private-secret", "PGSSLROOTCERT", "fixture.pem"));
        process.environment().remove("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE");
        if (invalidKey != null) {
            if (invalidKey.equals("DB_PASSWORD")) process.environment().remove(invalidKey);
            else process.environment().put(invalidKey, "false");
        }
        var running = process.start();
        if (!running.waitFor(30, TimeUnit.SECONDS)) {
            running.destroyForcibly();
            throw new AssertionError("Launcher timed out");
        }
        assertThat(running.exitValue()).isEqualTo(exit);
        return Files.readString(output);
    }
}
