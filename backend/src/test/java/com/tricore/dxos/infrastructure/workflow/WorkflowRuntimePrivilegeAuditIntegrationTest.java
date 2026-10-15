package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Read-only TLS fixture: no role/schema/ACL provisioning, migrations or DML. */
@EnabledIfEnvironmentVariable(named = "WORKFLOW_AUDIT_LOCAL_JDBC_URL", matches = ".+")
class WorkflowRuntimePrivilegeAuditIntegrationTest {
    @Test void actualTlsAuditReportsUnprovisionedRolesWithoutCreatingThem() throws Exception {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int exit = WorkflowRuntimePrivilegeAudit.run(environment(), WorkflowRuntimePrivilegeAuditTest.SQL,
                (ignored, properties) -> DriverManager.getConnection(localUrl().replace("127.0.0.1", "localhost"), properties),
                new PrintStream(output), new PrintStream(errors));
        assertThat(exit).as(errors.toString()).isZero();
        assertThat(output.toString()).contains("RUNTIME_PRIVILEGE_AUDIT=PASS", "transaction_read_only=on",
                "\"pooler_backend_ssl\":true", "\"public_temp\":true", "roles=NOT_PROVISIONED")
                .doesNotContain("jdbc:postgresql:");
        assertThat(errors.toString()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"ca", "hostname"})
    void actualVerifyFullRejectsWrongCaAndWrongHostname(String mismatch) throws Exception {
        var environment = environment();
        if (mismatch.equals("ca")) environment.put("PGSSLROOTCERT", required("WORKFLOW_AUDIT_LOCAL_WRONG_CA_CERT"));
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        String url = mismatch.equals("hostname") ? localUrl() : localUrl().replace("127.0.0.1", "localhost");
        int exit = WorkflowRuntimePrivilegeAudit.run(environment, WorkflowRuntimePrivilegeAuditTest.SQL,
                (ignored, properties) -> DriverManager.getConnection(url, properties),
                new PrintStream(output), new PrintStream(errors));
        assertThat(exit).isEqualTo(1);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString().trim()).isEqualTo("RUNTIME_PRIVILEGE_AUDIT=FAIL phase=connect_tls");
    }

    private Map<String, String> environment() {
        return new HashMap<>(Map.of("DB_HOST", "aws-0-fixture.pooler.supabase.com", "DB_PORT", "5432", "DB_NAME", "postgres",
                "DB_USER", "postgres", "DB_PASSWORD", required("WORKFLOW_AUDIT_LOCAL_DB_PASSWORD"),
                "PGSSLROOTCERT", required("WORKFLOW_AUDIT_LOCAL_CA_CERT")));
    }

    private String localUrl() {
        String url = required("WORKFLOW_AUDIT_LOCAL_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/postgres"))
            throw new IllegalArgumentException("Loopback fixture required");
        return url;
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing local TLS fixture");
        return value;
    }
}
