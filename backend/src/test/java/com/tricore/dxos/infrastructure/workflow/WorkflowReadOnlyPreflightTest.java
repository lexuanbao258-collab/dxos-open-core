package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WorkflowReadOnlyPreflightTest {
    @TempDir Path temporary;

    @Test void connectFailureReportsSafePhaseAndSqlState() throws Exception {
        var errors = new java.io.ByteArrayOutputStream();
        int exit = WorkflowReadOnlyPreflight.run(environment(), (url, properties) -> {
            throw new java.sql.SQLException("password=do-not-log; jdbc:postgresql://secret-host; token=secret-token", "08001");
        }, new java.io.PrintStream(new java.io.ByteArrayOutputStream()), new java.io.PrintStream(errors));
        assertThat(exit).isEqualTo(1);
        assertThat(errors.toString(java.nio.charset.StandardCharsets.UTF_8).trim())
                .isEqualTo("JDBC_PREFLIGHT_FAIL phase=connect sqlstate=08001");
    }

    @Test void tlsHandshakeFailureReportsPhaseWithoutDriverMessage() throws Exception {
        var errors = new java.io.ByteArrayOutputStream();
        int exit = WorkflowReadOnlyPreflight.run(environment(), (url, properties) -> {
            throw new SQLException("password=do-not-log", "08006",
                    new javax.net.ssl.SSLHandshakeException("token=do-not-log"));
        }, new java.io.PrintStream(new java.io.ByteArrayOutputStream()), new java.io.PrintStream(errors));
        assertThat(exit).isEqualTo(1);
        assertThat(errors.toString(java.nio.charset.StandardCharsets.UTF_8).trim())
                .isEqualTo("JDBC_PREFLIGHT_FAIL phase=tls_handshake sqlstate=08006");
    }

    @Test void validationAndUnexpectedFailuresNeverPrintSecrets() throws Exception {
        var missing = environment();
        missing.remove("DB_PASSWORD");
        assertFailure(missing, (url, properties) -> { throw new AssertionError("must not connect"); },
                "JDBC_PREFLIGHT_FAIL phase=validation error=invalid_configuration");
        assertFailure(environment(), (url, properties) -> {
            throw new SecurityException("password=do-not-log; token=do-not-log");
        }, "JDBC_PREFLIGHT_FAIL phase=connect error=access_denied");
    }

    @Test void sqlErrorsIdentifyTransactionCatalogAndPrivilegePhases() throws Exception {
        for (var caseEntry : Map.of(
                "SELECT pg_catalog.current_setting('transaction_read_only')", "read_only",
                "SELECT ssl FROM pg_catalog.pg_stat_ssl", "backend_tls",
                "pg_catalog.to_regnamespace('dxos_wf25_smoke_01') IS NOT NULL", "schema",
                "pg_catalog.version() AS version", "catalog_identity",
                "pg_catalog.has_database_privilege", "privilege_database_privileges",
                "AND c.relname='flyway_schema_history'", "privilege_flyway_history"
        ).entrySet()) {
            Connection connection = catalogConnection(caseEntry.getKey());
            assertFailure(environment(), (url, properties) -> connection,
                    "JDBC_PREFLIGHT_FAIL phase=" + caseEntry.getValue() + " sqlstate=42501");
            verify(connection).rollback();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", "FALSE", "NULL", "NO_ROW"})
    void backendSslStatusDoesNotOverrideVerifiedClientTls(String backendSsl) throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        var errors = new java.io.ByteArrayOutputStream();
        Connection connection = catalogConnection(null, backendSsl);
        int exit = WorkflowReadOnlyPreflight.run(environment(), (url, properties) -> connection,
                new java.io.PrintStream(output), new java.io.PrintStream(errors));
        assertThat(exit).as(errors.toString(java.nio.charset.StandardCharsets.UTF_8)).isZero();
        assertThat(errors.toString(java.nio.charset.StandardCharsets.UTF_8)).isEmpty();
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("client_pooler_tls=VERIFIED_VERIFY_FULL")
                .contains("pooler_backend_ssl=" + backendSsl)
                .contains("READ_ONLY_CATALOG_COMPLETE");
        verify(connection).rollback();
    }

    @Test void transactionAndDatabaseIdentityFailuresAreClassified() throws Exception {
        Connection transaction = catalogConnection(null);
        doThrow(new SQLException("password=do-not-log", "25001"))
                .when(transaction).setAutoCommit(false);
        assertFailure(environment(), (url, properties) -> transaction,
                "JDBC_PREFLIGHT_FAIL phase=transaction sqlstate=25001");

        Connection identity = catalogConnection(null);
        when(identity.getCatalog()).thenThrow(new SQLException("password=do-not-log", "3D000"));
        assertFailure(environment(), (url, properties) -> identity,
                "JDBC_PREFLIGHT_FAIL phase=database_identity sqlstate=3D000");
        verify(identity).rollback();
    }

    @Test void rollbackAndCloseFailuresDoNotClaimCompletion() throws Exception {
        Connection rollback = catalogConnection(null);
        doThrow(new SQLException("password=do-not-log", "08006")).when(rollback).rollback();
        assertFailure(environment(), (url, properties) -> rollback,
                "JDBC_PREFLIGHT_FAIL phase=rollback sqlstate=08006");

        Connection close = catalogConnection(null);
        doThrow(new SQLException("password=do-not-log", "08006")).when(close).close();
        assertFailure(environment(), (url, properties) -> close,
                "JDBC_PREFLIGHT_FAIL phase=close sqlstate=08006");
    }

    @Test void rollbackFailureDoesNotHideOriginalCatalogFailure() throws Exception {
        Connection connection = catalogConnection("pg_catalog.version() AS version");
        doThrow(new SQLException("password=do-not-log", "08006")).when(connection).rollback();
        assertFailure(environment(), (url, properties) -> connection,
                "JDBC_PREFLIGHT_FAIL phase=catalog_identity sqlstate=42501" + System.lineSeparator()
                + "JDBC_PREFLIGHT_FAIL phase=rollback sqlstate=08006");
    }

    @Test void invalidSqlStateCannotInjectOutput() throws Exception {
        Connection connection = catalogConnection(null);
        when(connection.getCatalog()).thenThrow(new SQLException("password=do-not-log", "08006\nsecret"));
        assertFailure(environment(), (url, properties) -> connection,
                "JDBC_PREFLIGHT_FAIL phase=database_identity sqlstate=UNKNOWN");
    }

    private void assertFailure(Map<String, String> env, WorkflowReadOnlyPreflight.Connector connector,
                               String expected) {
        var output = new java.io.ByteArrayOutputStream();
        var errors = new java.io.ByteArrayOutputStream();
        int exit = WorkflowReadOnlyPreflight.run(env, connector,
                new java.io.PrintStream(output), new java.io.PrintStream(errors));
        assertThat(exit).isEqualTo(1);
        assertThat(errors.toString(java.nio.charset.StandardCharsets.UTF_8).trim()).isEqualTo(expected);
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("READ_ONLY_CATALOG_COMPLETE");
    }

    private Connection catalogConnection(String failingSql) throws SQLException {
        return catalogConnection(failingSql, "TRUE");
    }

    private Connection catalogConnection(String failingSql, String backendSsl) throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn("postgres");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (failingSql != null && sql.contains(failingSql))
                throw new SQLException("password=do-not-log; jdbc:postgresql://secret-host; token=do-not-log", "42501");
            ResultSet rows = mock(ResultSet.class);
            if (sql.contains("pg_catalog.to_regnamespace('dxos_wf25_smoke_01') IS NOT NULL")) {
                when(rows.next()).thenReturn(true, false);
                when(rows.getString(1)).thenReturn("on");
                when(rows.getString(2)).thenReturn("dxos_wf25_smoke_01,pg_catalog,pg_temp");
            } else if (sql.contains("SELECT pg_catalog.current_setting('transaction_read_only')")) {
                when(rows.next()).thenReturn(true, false);
                when(rows.getString(1)).thenReturn("on");
            } else if (sql.contains("SELECT ssl FROM pg_catalog.pg_stat_ssl")) {
                when(rows.next()).thenReturn(!"NO_ROW".equals(backendSsl), false);
                when(rows.getBoolean(1)).thenReturn("TRUE".equals(backendSsl));
                when(rows.wasNull()).thenReturn("NULL".equals(backendSsl));
            }
            return rows;
        });
        return connection;
    }

    private Map<String, String> environment() throws Exception {
        Path ca = Files.writeString(temporary.resolve("ca.pem"), "test fixture only");
        return new HashMap<>(Map.of("DB_HOST", "aws-0-region.pooler.supabase.com",
                "DB_PORT", "5432", "DB_NAME", "postgres", "DB_USER", "test-role.test-ref",
                "DB_PASSWORD", "test-only", "PGSSLROOTCERT", ca.toString()));
    }

    @ParameterizedTest @ValueSource(strings={"DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD", "PGSSLROOTCERT"})
    void missingEnvironmentReportsOnlyTheMissingName(String name) throws Exception {
        var environment = environment(); environment.remove(name);
        assertThatThrownBy(() -> WorkflowReadOnlyPreflight.connectionProperties(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing environment variables: " + name);
    }

    @Test void verifiedTlsAndReadOnlyCannotBeOverriddenByCredentials() throws Exception {
        var environment = environment();
        environment.put("DB_PASSWORD", "secret?sslmode=disable&options=-cdefault_transaction_read_only=off");
        environment.put("DB_SSLMODE", "require");
        environment.put("DB_SSLHOSTNAMEVERIFIER", "accept-all");
        var properties = WorkflowReadOnlyPreflight.connectionProperties(environment);
        assertThat(properties.getProperty("sslmode")).isEqualTo("verify-full");
        assertThat(properties.getProperty("sslrootcert")).isEqualTo(environment.get("PGSSLROOTCERT"));
        assertThat(properties.getProperty("sslhostnameverifier")).isNull();
        assertThat(properties.getProperty("sslfactory")).isNull();
        assertThat(properties.getProperty("options")).isEqualTo("-c default_transaction_read_only=on");
        assertThat(properties.getProperty("currentSchema")).isEqualTo("dxos_wf25_smoke_01,pg_catalog,pg_temp");
    }

    @Test void directConnectionUsesExistingCredentialsWithVerifiedTlsAndReadOnly() throws Exception {
        var environment = environment();
        environment.put("DB_HOST", "db.abcdefghijklmnopqrst.supabase.co");
        environment.put("DB_USER", "postgres");
        var output = new java.io.ByteArrayOutputStream();
        var connection = catalogConnection(null, "TRUE");
        int exit = WorkflowReadOnlyPreflight.run(environment, (url, properties) -> {
            assertThat(url).isEqualTo("jdbc:postgresql://db.abcdefghijklmnopqrst.supabase.co:5432/postgres");
            assertThat(properties.getProperty("user")).isEqualTo("postgres");
            assertThat(properties.getProperty("password")).isEqualTo(environment.get("DB_PASSWORD"));
            assertThat(properties.getProperty("sslmode")).isEqualTo("verify-full");
            assertThat(properties.getProperty("sslrootcert")).isEqualTo(environment.get("PGSSLROOTCERT"));
            assertThat(properties.getProperty("options")).isEqualTo("-c default_transaction_read_only=on");
            return connection;
        }, new java.io.PrintStream(output), new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
        assertThat(exit).isZero();
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("client_direct_tls=VERIFIED_VERIFY_FULL", "direct_backend_ssl=TRUE")
                .doesNotContain("client_pooler_tls", "pooler_backend_ssl", environment.get("DB_PASSWORD"));
        verify(connection).rollback();
        verify(connection).close();
    }

    @ParameterizedTest @ValueSource(strings={"127.0.0.1", "other.example.com", "aws.pooler.supabase.com?sslmode=disable", "user@aws.pooler.supabase.com", "db.abcdefghijklmnopqrst.supabase.co?sslmode=disable", "db.abcdefghijklmnopqrst.supabase.co.evil.example"})
    void endpointOverridesAreRejected(String host) throws Exception {
        var environment = environment(); environment.put("DB_HOST", host);
        assertThatThrownBy(() -> WorkflowReadOnlyPreflight.connectionProperties(environment))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Supabase endpoint rejected");
    }
    @ParameterizedTest @ValueSource(strings={"postgres?sslmode=require", "postgres?sslmode=disable"})
    void databaseUrlOverridesAreRejected(String database) throws Exception {
        var environment = environment();
        environment.put("DB_NAME", database);
        assertThatThrownBy(() -> WorkflowReadOnlyPreflight.connectionProperties(environment))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Database identifier rejected");
    }

    @Test void missingCaStopsBeforeConnection() throws Exception {
        var environment = environment(); environment.put("PGSSLROOTCERT", temporary.resolve("missing.pem").toString());
        assertThatThrownBy(() -> WorkflowReadOnlyPreflight.connectionProperties(environment))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CA certificate missing");
    }
}
