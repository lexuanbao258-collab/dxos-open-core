package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowApprovedSmokeLauncherTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"no-opt-in", "both-modes", "wrong-host", "wrong-ca", "local-flag"})
    void validationStopsBeforePasswordOrChild(String fault) throws Exception {
        var args = new ArrayList<>(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-File",
                Path.of("../scripts/workflow-approved-smoke.ps1").toAbsolutePath().normalize().toString(),
                "-CaCertificate", temporary.resolve("absent").toString()));
        if (!fault.equals("no-opt-in")) args.add("-ExecuteApprovedSmoke");
        if (fault.equals("both-modes")) args.add("-AuditOnly");
        var builder = new ProcessBuilder(args).redirectErrorStream(true);
        clear(builder);
        if (fault.equals("wrong-host")) builder.environment().put("DB_HOST", "other.example");
        if (fault.equals("local-flag")) builder.environment().put("WF25_DISPOSABLE_TEST", "false");
        assertThat(child(builder, 1)).contains("CLOUD_SMOKE_EXECUTION=NOT_EXECUTED", "SMOKE_PHASE=launcher_validation")
                .doesNotContain("password (hidden)", "Gradle");
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "lost-ack", "read-failure", "post-drift", "preexisting", "missing-precheck", "duplicate-precheck", "secret-output", "audit-only"})
    void orchestrationIsFailClosedAndNeverRetries(String fault) throws Exception {
        var scripts = Files.createDirectory(temporary.resolve("scripts"));
        var backend = Files.createDirectory(temporary.resolve("backend"));
        String source = Files.readString(Path.of("../scripts/workflow-approved-smoke.ps1"));
        assertThat(source.split("    Assert-WarpIpv6\\r?\\n", -1)).hasSize(2);
        // Only this disposable launcher fixture replaces network and interactive input.
        source = source.replaceAll("    Assert-WarpIpv6\\r?\\n", "    # fixture network check\\n")
                .replace("Read-Host 'Supabase postgres password (hidden)' -AsSecureString",
                        "ConvertTo-SecureString 'private-unit-password' -AsPlainText -Force");
        Files.writeString(scripts.resolve("workflow-approved-smoke.ps1"), source);
        var ca = Files.writeString(temporary.resolve("ca.pem"), "fixture only");
        String zero = "0".repeat(64), one = "1".repeat(64);
        String stub = """
                @echo off
                if "%~1" NEQ "workflowDevelopmentSmoke" exit /b 1
                if "%~2"=="--args=--development-integration smoke cloud-20261015-01" goto smoke
                if defined WORKFLOW_DEV_WRITE_APPROVAL exit /b 1
                if "%~2"=="--args=--development-integration read cloud-20261015-01" goto read
                if "%~2" NEQ "--args=--development-integration audit cloud-20261015-01" exit /b 1
                echo DEVELOPMENT_AUDIT_PASS
                echo DEVELOPMENT_CATALOG_SHA256=ZERO
                if exist smoke-started goto post
                echo DEVELOPMENT_INSTANCES=PRE
                echo DEVELOPMENT_HISTORY=0
                echo DEVELOPMENT_RUN_INSTANCES=0
                echo DEVELOPMENT_RUN_HISTORY=0
                PREEXTRA
                exit /b 0
                :post
                echo DEVELOPMENT_INSTANCES=2
                echo DEVELOPMENT_HISTORY=4
                echo DEVELOPMENT_RUN_INSTANCES=2
                echo DEVELOPMENT_RUN_HISTORY=4
                exit /b 0
                :smoke
                if not "%WORKFLOW_DEV_WRITE_APPROVAL%"=="dxos_wf25_smoke_01" exit /b 1
                if exist smoke-started exit /b 1
                echo fixture>smoke-started
                SMOKE
                exit /b 0
                :read
                READ
                exit /b 0
                """.replace("ZERO", zero).replace("PREEXTRA", fault.equals("duplicate-precheck") ? "echo DEVELOPMENT_HISTORY=0" : "")
                .replace("PRE", fault.equals("preexisting") ? "1" : "0")
                .replace("SMOKE", fault.equals("lost-ack") ? "exit /b 1" : "echo DEVELOPMENT_SMOKE_PASS")
                .replace("READ", fault.equals("read-failure") ? "exit /b 1" : "echo DEVELOPMENT_RESTART_READ_PASS");
        if (fault.equals("missing-precheck")) stub = stub.replace("echo DEVELOPMENT_RUN_HISTORY=0", "");
        if (fault.equals("post-drift")) stub = stub.replace("echo DEVELOPMENT_CATALOG_SHA256=" + zero,
                "if exist smoke-started (echo DEVELOPMENT_CATALOG_SHA256=" + one
                        + ") else echo DEVELOPMENT_CATALOG_SHA256=" + zero);
        if (fault.equals("secret-output")) stub = stub.replace("@echo off", "@echo off\necho private-unit-password jdbc:postgresql://private-host");
        Files.writeString(backend.resolve("gradlew.bat"), stub.replace("\n", "\r\n"));
        var builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-File",
                scripts.resolve("workflow-approved-smoke.ps1").toString(),
                fault.equals("audit-only") ? "-AuditOnly" : "-ExecuteApprovedSmoke",
                "-CaCertificate", ca.toString(), "-JavaHome", System.getProperty("java.home")).redirectErrorStream(true);
        clear(builder);
        boolean success = List.of("success", "secret-output", "audit-only").contains(fault);
        String output = child(builder, success ? 0 : 1);
        assertThat(output).doesNotContain("private-unit-password", "private-host", "jdbc:postgresql:");
        String status = success ? fault.equals("audit-only") ? "AUDIT_ONLY" : "COMMITTED_PASS"
                : List.of("preexisting", "missing-precheck", "duplicate-precheck").contains(fault) ? "NOT_EXECUTED" : "COMMIT_OUTCOME_UNKNOWN";
        assertThat(output).contains("CLOUD_SMOKE_EXECUTION=" + status);
        assertThat(Files.exists(backend.resolve("smoke-started"))).isEqualTo(!status.equals("NOT_EXECUTED") && !fault.equals("audit-only"));
    }

    private static void clear(ProcessBuilder builder) {
        builder.environment().keySet().removeIf(k -> k.startsWith("DB_") || k.startsWith("WORKFLOW_") || k.equals("WF25_DISPOSABLE_TEST"));
    }

    private static String child(ProcessBuilder builder, int expectedExit) throws Exception {
        var process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).withFailMessage(output).isEqualTo(expectedExit);
            return output;
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
