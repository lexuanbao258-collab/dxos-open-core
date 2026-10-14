package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowSchemaBootstrapTest {
    @TempDir Path temporary;
    Map<String, String> environment;
    Path bundle;

    @BeforeEach void setup() throws Exception {
        bundle = Files.createDirectory(temporary.resolve("bundle"));
        for (String name : List.of("V3__create_workflow_persistence.sql", "V4__seed_workflow_definitions.sql"))
            Files.copy(Path.of("src/main/resources/db/migration", name), bundle.resolve(name));
        environment = new HashMap<>(Map.of("DB_HOST", WorkflowSchemaBootstrap.HOST, "DB_PORT", "5432",
                "DB_NAME", "postgres", "DB_USER", "postgres", "DB_PASSWORD", "never-print-this-password",
                "PGSSLROOTCERT", Files.writeString(temporary.resolve("ca.pem"), "unit fixture").toString(),
                "WORKFLOW_SCHEMA_BOOTSTRAP_APPROVAL", WorkflowSchemaBootstrap.SCHEMA));
    }

    @Test void exactApprovedEndpointCannotDowngradeTlsOrReadOnlyPrechecks() {
        environment.put("DB_SSLMODE", "disable");
        environment.put("DB_SSLHOSTNAMEVERIFIER", "accept-all");
        var properties = WorkflowSchemaBootstrap.properties(environment);
        assertThat(properties.getProperty("sslmode")).isEqualTo("verify-full");
        assertThat(properties.getProperty("sslrootcert")).isEqualTo(environment.get("PGSSLROOTCERT"));
        assertThat(properties.getProperty("options")).isEqualTo("-c default_transaction_read_only=on");
        assertThat(properties.getProperty("sslfactory")).isNull();
        assertThat(properties.getProperty("sslhostnameverifier")).isNull();
    }

    @ParameterizedTest @ValueSource(strings={"aws-0-region.pooler.supabase.com", "db.differentprojectref.supabase.co",
            "127.0.0.1", "db.zsfctpnitkdztlfhzqgw.supabase.co?sslmode=disable"})
    void noOtherEndpointCanBeUsed(String host) {
        environment.put("DB_HOST", host); assertStopped();
    }

    @ParameterizedTest @ValueSource(strings={"DB_PORT","DB_NAME","DB_USER","DB_PASSWORD","PGSSLROOTCERT","WORKFLOW_SCHEMA_BOOTSTRAP_APPROVAL"})
    void missingOrWrongSettingsStopBeforeConnect(String key) {
        environment.put(key, ""); assertStopped();
    }

    @ParameterizedTest @ValueSource(strings={"WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE","WF25_DISPOSABLE_TEST","WORKFLOW_DEV_LOCAL_DISPOSABLE"})
    void anyLocalFlagIsRejectedEvenWhenFalse(String key) {
        environment.put(key,"false"); assertStopped();
    }

    @Test void bundleDriftStopsBeforeConnect() throws Exception {
        Files.writeString(bundle.resolve("V4__seed_workflow_definitions.sql"), "changed"); assertStopped();
    }

    @Test void connectionFailuresNeverExposeSecretsOrClaimCloudSuccess() {
        var result = WorkflowSchemaBootstrap.run(environment, bundle, (url, properties) -> {
            assertThat(url).isEqualTo(WorkflowSchemaBootstrap.URL);
            throw new java.sql.SQLException("password=never-print-this-password jdbc:postgresql://private-host", "08006");
        }, mock(WorkflowEventTriggerGuard.class));
        var output = new ByteArrayOutputStream();
        assertThat(result.report(new PrintStream(output))).isEqualTo(1);
        assertThat(output.toString()).contains("SCHEMA_BOOTSTRAP=NOT_EXECUTED", "POSTCOMMIT_AUDIT=NOT_RUN")
                .doesNotContain("never-print-this-password", "private-host", "jdbc:postgresql:");
    }

    @ParameterizedTest @ValueSource(strings={"no-opt-in","wrong-host","missing-ca"})
    void powershellLauncherRejectsBeforePromptOrGradle(String failure) throws Exception {
        var args = new ArrayList<>(List.of("powershell.exe","-NoProfile","-NonInteractive","-File",
                Path.of("../scripts/workflow-schema-bootstrap.ps1").toAbsolutePath().normalize().toString(),
                "-CaCertificate", environment.get("PGSSLROOTCERT")));
        if (!failure.equals("no-opt-in")) args.add("-ExecuteApprovedBootstrap");
        if (failure.equals("missing-ca")) args.set(args.indexOf("-CaCertificate") + 1, temporary.resolve("missing-ca").toString());
        var builder = new ProcessBuilder(args).redirectErrorStream(true);
        builder.environment().keySet().removeIf(key -> key.startsWith("DB_") || key.startsWith("WORKFLOW_") || key.equals("WF25_DISPOSABLE_TEST"));
        if (failure.equals("wrong-host")) builder.environment().put("DB_HOST", "other.example.com");
        var process = builder.start();
        try {
            assertThat(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue()).isEqualTo(1);
            assertThat(output).contains("SCHEMA_BOOTSTRAP=NOT_EXECUTED", "BOOTSTRAP_PHASE=launcher_validation")
                    .doesNotContain("password (hidden)", "never-print-this-password", "Gradle");
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    @Test void missingChildReportIsUnknownRatherThanClaimingNoSchemaWrite() throws Exception {
        Path scripts=Files.createDirectory(temporary.resolve("scripts"));
        Path backend=Files.createDirectory(temporary.resolve("backend"));
        Path launcher=scripts.resolve("workflow-schema-bootstrap.ps1");
        Files.copy(Path.of("../scripts/workflow-schema-bootstrap.ps1"),launcher);
        Files.writeString(backend.resolve("gradlew.bat"),"@echo off\r\nexit /b 1\r\n");
        var builder=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-File",launcher.toString(),
                "-ExecuteApprovedBootstrap","-CaCertificate",environment.get("PGSSLROOTCERT"),
                "-JavaHome",System.getProperty("java.home")).redirectErrorStream(true);
        builder.environment().keySet().removeIf(key->key.startsWith("DB_")||key.startsWith("WORKFLOW_")||key.equals("WF25_DISPOSABLE_TEST"));
        builder.environment().put("DB_PASSWORD",environment.get("DB_PASSWORD"));
        var process=builder.start();
        try{
            assertThat(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue()).isEqualTo(1);
            assertThat(output).contains("SCHEMA_BOOTSTRAP=COMMIT_UNKNOWN","POSTCOMMIT_AUDIT=NOT_RUN","BOOTSTRAP_PHASE=java_bootstrap",
                            "OTHER_CLOUD_WRITES=UNVERIFIED_POSTCOMMIT_AUDIT_NOT_CONFIRMED")
                    .doesNotContain("OTHER_CLOUD_WRITES=NONE")
                    .doesNotContain("SCHEMA_BOOTSTRAP=NOT_EXECUTED",environment.get("DB_PASSWORD"));
        }finally{if(process.isAlive())process.destroyForcibly();}
    }

    private void assertStopped() {
        var guard = mock(WorkflowEventTriggerGuard.class);
        var result = WorkflowSchemaBootstrap.run(environment, bundle,
                (url, properties) -> { throw new AssertionError("Must not connect"); }, guard);
        assertThat(result.status()).isEqualTo("NOT_EXECUTED");
        verifyNoInteractions(guard);
    }
}
