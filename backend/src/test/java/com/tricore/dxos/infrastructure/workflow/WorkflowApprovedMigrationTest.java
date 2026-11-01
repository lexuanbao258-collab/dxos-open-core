package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowApprovedMigrationTest {
    @TempDir Path temporary;
    Map<String,String> env;
    Path bundle;
    @BeforeEach void setup() throws Exception {
        bundle=Files.createDirectory(temporary.resolve("bundle"));
        for(String name:List.of("V3__create_workflow_persistence.sql","V4__seed_workflow_definitions.sql"))
            Files.copy(Path.of("src/main/resources/db/migration",name),bundle.resolve(name));
        env=new HashMap<>(Map.of("DB_HOST",WorkflowSchemaBootstrap.HOST,"DB_PORT","5432","DB_NAME","postgres",
            "DB_USER","postgres","DB_PASSWORD","private-unit-password",
            "PGSSLROOTCERT",Files.writeString(temporary.resolve("ca.pem"),"unit fixture").toString(),
            "WORKFLOW_FLYWAY_APPROVAL",WorkflowApprovedMigration.S));
    }
    @Test void tlsCannotBeOverriddenAndWriterIsNotProductionDefault() {
        env.put("DB_SSLMODE","disable");
        env.put("DB_SSLHOSTNAMEVERIFIER","accept-all");
        var read=WorkflowApprovedMigration.properties(env,false);
        var write=WorkflowApprovedMigration.properties(env,true);
        assertThat(read.getProperty("sslmode")).isEqualTo("verify-full");
        assertThat(write.getProperty("sslmode")).isEqualTo("verify-full");
        assertThat(read.getProperty("options")).contains("default_transaction_read_only=on");
        assertThat(write.getProperty("options")).doesNotContain("default_transaction_read_only");
        assertThat(write.getProperty("currentSchema")).isEqualTo(WorkflowApprovedMigration.S+",pg_catalog,pg_temp");
        assertThat(read.getProperty("currentSchema")).isEqualTo(write.getProperty("currentSchema"));
        assertThat(read.getProperty("sslfactory")).isNull();
        assertThat(read.getProperty("sslhostnameverifier")).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"aws-0-region.pooler.supabase.com","db.differentprojectref.supabase.co","localhost",
        "db.zsfctpnitkdztlfhzqgw.supabase.co?sslmode=disable"})
    void wrongHostStopsBeforeConnecting(String host) {env.put("DB_HOST",host);stopped();}
    @ParameterizedTest @ValueSource(strings={"DB_PORT","DB_NAME","DB_USER","WORKFLOW_FLYWAY_APPROVAL"})
    void wrongIdentityOrApprovalStopsBeforeConnecting(String key) {env.put(key,"wrong");stopped();}
    @ParameterizedTest @ValueSource(strings={"WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE","WORKFLOW_DEV_LOCAL_DISPOSABLE","WF25_DISPOSABLE_TEST"})
    void anyLocalFlagIsRejectedEvenWhenFalse(String key) {env.put(key,"false");stopped();}
    @Test void bundleSidecarsAreRejectedBeforeConnection() throws Exception {
        Files.writeString(bundle.resolve("V4__seed_workflow_definitions.sql.conf"),"executeInTransaction=false");stopped();
    }
    @Test void packagedManifestRetainsReviewedBytes() throws Exception {
        try (var stream = WorkflowApprovedMigration.class.getResourceAsStream("/workflow-event-trigger-manifest.json")) {
            assertThat(stream).isNotNull();
            assertThat(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(stream.readAllBytes()))).isEqualTo(WorkflowApprovedMigration.MANIFEST_SHA);
        }
        WorkflowApprovedMigration.bundle(bundle);
    }
    @Test void driverErrorsCannotLeakSecretsOrClaimAWrite() {
        var result=WorkflowApprovedMigration.run(env,bundle,(u,p)->{throw new java.sql.SQLException("password=private-unit-password jdbc:postgresql://private-host","08006");},
                mock(WorkflowEventTriggerGuard.class),f->{throw new AssertionError("No migration");});
        var out=new ByteArrayOutputStream();
        assertThat(result.report(new PrintStream(out))).isEqualTo(1);
        assertThat(out.toString()).contains("FLYWAY_EXECUTION=NOT_EXECUTED","OTHER_CLOUD_WRITES=NONE","NEXT_APPROVAL_REQUIRED=YES")
                .contains("FAILURE_CODE=CONNECTION_OR_TLS_FAILED","FAILURE_SQLSTATE=08006")
                .doesNotContain("private-unit-password","private-host","jdbc:postgresql:");
    }
    @ParameterizedTest @ValueSource(strings={"28P01","3D000"})
    void authenticationAndConnectionDiagnosticsUseOnlyStructuredState(String state) {
        var result=WorkflowApprovedMigration.run(env,bundle,(u,p)->{
            throw new java.sql.SQLException("password=private-unit-password jdbc:postgresql://private-host",state);
        },mock(WorkflowEventTriggerGuard.class),f->{throw new AssertionError("No migration");});
        assertThat(result.sqlState()).isEqualTo(state);
        assertThat(result.failureCode()).isEqualTo(state.equals("28P01")?"AUTHENTICATION_FAILED":"CONNECTION_OR_TLS_FAILED");
        assertThat(result.direct() || result.tls()).isFalse();
        var output=new ByteArrayOutputStream();
        result.report(new PrintStream(output));
        assertThat(output.toString()).doesNotContain("private-unit-password","private-host","jdbc:postgresql:");
    }
    @ParameterizedTest @ValueSource(strings={"no-opt-in","wrong-host","wrong-ca","local-flag"})
    void launcherFailsBeforePasswordPromptOrGradle(String fault) throws Exception {
        var args=new ArrayList<>(List.of("powershell.exe","-NoProfile","-NonInteractive","-File",
                Path.of("../scripts/workflow-approved-migrations.ps1").toAbsolutePath().normalize().toString(),
                "-CaCertificate",env.get("PGSSLROOTCERT")));
        if(!fault.equals("no-opt-in"))args.add("-ExecuteApprovedMigrations");
        if(fault.equals("wrong-ca"))args.set(args.indexOf("-CaCertificate")+1,temporary.resolve("absent").toString());
        var builder=new ProcessBuilder(args).redirectErrorStream(true);
        clear(builder);
        if(fault.equals("wrong-host"))builder.environment().put("DB_HOST","different.example");
        if(fault.equals("local-flag"))builder.environment().put("WF25_DISPOSABLE_TEST","false");
        var output=child(builder,1);
        assertThat(output).contains("FLYWAY_EXECUTION=NOT_EXECUTED","MIGRATION_PHASE=launcher_validation")
                .doesNotContain("password (hidden)","private-unit-password","Gradle");
    }
    @ParameterizedTest @ValueSource(strings={"missing","partial","secret-output","success"})
    void launcherHandoffAndFilteringAreFailClosed(String fault) throws Exception {
        var scripts=Files.createDirectory(temporary.resolve("scripts"));
        var backend=Files.createDirectory(temporary.resolve("backend"));
        var source=Files.readString(Path.of("../scripts/workflow-approved-migrations.ps1"));
        assertThat(source.split("    Assert-WarpIpv6\\r?\\n",-1)).hasSize(2);
        // Only the owned launcher fixture mocks the WARP/DNS service; production gates are unchanged.
        Files.writeString(scripts.resolve("workflow-approved-migrations.ps1"),
                source.replaceAll("    Assert-WarpIpv6\\r?\\n","    # disposable launcher fixture: WARP/DNS mocked\n"));
        String report="""
            echo FLYWAY_EXECUTION=COMMITTED_PASS
            echo DIRECT_CONNECTION=PASS
            echo TLS_VERIFIED=YES
            echo V3_STATUS=SUCCESS_PIN_MATCH
            echo V4_STATUS=SUCCESS_PIN_MATCH
            echo FLYWAY_HISTORY=V3_V4_SUCCESS_CHECKSUM_MATCH
            echo INVENTORY_CHECK=PASS
            echo SEED_CHECK=PASS
            echo POSTCOMMIT_AUDIT=PASS
            echo OTHER_CLOUD_WRITES=NONE
            echo MIGRATION_PHASE=fresh_read_only_audit_pass
            echo NEXT_APPROVAL_REQUIRED=YES
            echo FAILURE_CODE=NONE
            echo FAILURE_SQLSTATE=NONE
            """;
        String body=switch(fault) {
            case "missing" -> "exit /b 1\n";
            case "partial" -> "echo FLYWAY_EXECUTION=COMMITTED_PASS\nexit /b 0\n";
            case "secret-output" -> report+"echo private-unit-password jdbc:postgresql://private-host\n"
                    +"echo V3_STATUS=private-unit-password\nexit /b 0\n";
            default -> report+"exit /b 0\n";
        };
        Files.writeString(backend.resolve("gradlew.bat"),("@echo off\n"+body).replace("\n","\r\n"));
        var builder=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-File",
                scripts.resolve("workflow-approved-migrations.ps1").toString(),"-ExecuteApprovedMigrations",
                "-CaCertificate",env.get("PGSSLROOTCERT"),"-JavaHome",System.getProperty("java.home")).redirectErrorStream(true);
        clear(builder);builder.environment().put("DB_PASSWORD","private-unit-password");
        var output=child(builder,fault.equals("missing")||fault.equals("partial")?1:0);
        assertThat(output).doesNotContain("private-unit-password","private-host","jdbc:postgresql:");
        assertThat(output).contains("FLYWAY_EXECUTION="+(fault.equals("missing")||fault.equals("partial")?"COMMIT_UNKNOWN":"COMMITTED_PASS"));
        assertThat(output).contains("FAILURE_CODE="+(fault.equals("missing")||fault.equals("partial")?"CHILD_REPORT_MISSING":"NONE"));
    }
    private void stopped() {
        var guard=mock(WorkflowEventTriggerGuard.class);
        var result=WorkflowApprovedMigration.run(env,bundle,(u,p)->{throw new AssertionError("No connection");},guard,
                f->{throw new AssertionError("No migration");});
        assertThat(result.status()).isEqualTo("NOT_EXECUTED");verifyNoInteractions(guard);
    }
    private static void clear(ProcessBuilder builder) {
        builder.environment().keySet().removeIf(k->k.startsWith("DB_")||k.startsWith("WORKFLOW_")||k.equals("WF25_DISPOSABLE_TEST"));
    }
    private static String child(ProcessBuilder builder,int exit) throws Exception {
        var p=builder.start();
        try {
            assertThat(p.waitFor(20,TimeUnit.SECONDS)).isTrue();
            String out=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(p.exitValue()).withFailMessage(out).isEqualTo(exit);return out;
        } finally {if(p.isAlive())p.destroyForcibly();}
    }
}
