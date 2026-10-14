package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowLiveGuardTest {
    @TempDir Path temporary;

    @Test void verifiesServerReadOnlyBeforeCatalogAndRollsBackBeforePass() throws Exception {
        Connection connection = connection("on");
        var guard = mock(WorkflowEventTriggerGuard.class);
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int exit = WorkflowLiveGuard.run(environment(), bundle(), (url, properties) -> {
            assertThat(properties.getProperty("sslmode")).isEqualTo("verify-full");
            assertThat(properties.getProperty("sslfactory")).isNull();
            assertThat(properties.getProperty("sslhostnameverifier")).isNull();
            assertThat(properties.getProperty("options")).isEqualTo("-c default_transaction_read_only=on");
            assertThat(properties.getProperty("readOnly")).isEqualTo("true");
            assertThat(properties.getProperty("readOnlyMode")).isEqualTo("transaction");
            return connection;
        }, guard, new PrintStream(output), new PrintStream(errors));
        assertThat(exit).isZero();
        assertThat(errors.toString()).isEmpty();
        assertThat(output.toString()).contains("LIVE_GUARD=PASS scope=READ_ONLY_CATALOG", "pooler_backend_ssl=FALSE",
                "connection_mode=SESSION_POOLER", "client_pooler_tls=VERIFIED_VERIFY_FULL", "endpoint_port=5432",
                "schema_target=ABSENT", "samples=5", "WRITE_READINESS=BLOCKED", "backend_tls=UNACCEPTED")
                .doesNotContain("test-only", "jdbc:postgresql:", "test-role.test-ref");
        var order = inOrder(connection, guard);
        order.verify(connection).setReadOnly(true);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        order.verify(connection).setAutoCommit(false);
        order.verify(guard, times(WorkflowLiveGuard.SAMPLES)).verifyReadOnly(connection, WorkflowSmokeRunner.SCHEMA);
        order.verify(connection).rollback();
        order.verify(connection).close();
        verify(guard, never()).verify(any(), anyString());
        verify(connection, never()).commit();
    }

    @Test void directConnectionReportsDirectTlsWithoutPoolerLabelsOrCredentials() throws Exception {
        var environment = environment();
        environment.put("DB_HOST", "db.abcdefghijklmnopqrst.supabase.co");
        environment.put("DB_USER", "postgres");
        Connection connection = connection("on");
        ResultSet ssl = mock(ResultSet.class);
        when(ssl.next()).thenReturn(true, false);
        when(ssl.getBoolean(1)).thenReturn(true);
        when(connection.createStatement().executeQuery("SELECT ssl FROM pg_catalog.pg_stat_ssl WHERE pid=pg_catalog.pg_backend_pid()"))
                .thenReturn(ssl);
        var output = new ByteArrayOutputStream();
        int exit = WorkflowLiveGuard.run(environment, bundle(), (url, properties) -> {
            assertThat(url).isEqualTo("jdbc:postgresql://db.abcdefghijklmnopqrst.supabase.co:5432/postgres");
            assertThat(properties.getProperty("sslmode")).isEqualTo("verify-full");
            assertThat(properties.getProperty("sslrootcert")).isEqualTo(environment.get("PGSSLROOTCERT"));
            assertThat(properties.getProperty("options")).isEqualTo("-c default_transaction_read_only=on");
            return connection;
        }, mock(WorkflowEventTriggerGuard.class), new PrintStream(output), new PrintStream(new ByteArrayOutputStream()));
        assertThat(exit).isZero();
        assertThat(output.toString()).contains("connection_mode=DIRECT", "endpoint_port=5432",
                "client_direct_tls=VERIFIED_VERIFY_FULL", "direct_backend_ssl=TRUE")
                .doesNotContain("client_pooler_tls", "pooler_backend_ssl", environment.get("DB_PASSWORD"), environment.get("DB_HOST"));
        verify(connection).rollback();
        verify(connection).close();
        verify(connection, never()).commit();
    }

    @ParameterizedTest @ValueSource(strings = {"off", "", "ON"})
    void serverMustConfirmReadOnly(String mode) throws Exception {
        var guard = mock(WorkflowEventTriggerGuard.class);
        Connection connection = connection(mode);
        assertFailure(environment(), bundle(), (url, properties) -> connection, guard, "read_only");
        verifyNoInteractions(guard);
        verify(connection).rollback();
        verify(connection).close();
    }

    @ParameterizedTest @ValueSource(strings = {"true", "false", ""})
    void cloudRejectsAnyLocalBypassSettingBeforeConnect(String local) throws Exception {
        var environment = environment();
        environment.put("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE", local);
        assertFailure(environment, bundle(), (url, properties) -> { throw new AssertionError("Must not connect"); },
                mock(WorkflowEventTriggerGuard.class), "validation");
    }

    @ParameterizedTest @ValueSource(strings = {"DB_HOST", "DB_PORT", "DB_NAME", "PGSSLROOTCERT"})
    void wrongCloudConfigurationStopsBeforeConnect(String key) throws Exception {
        var environment = environment();
        environment.put(key, "secret?sslmode=disable&token=private");
        assertFailure(environment, bundle(), (url, properties) -> { throw new AssertionError("Must not connect"); },
                mock(WorkflowEventTriggerGuard.class), "validation");
    }

    @Test void bundleDriftStopsBeforeConnect() throws Exception {
        Path bundle = bundle();
        Files.writeString(bundle.resolve("V4__seed_workflow_definitions.sql"), "private body");
        assertFailure(environment(), bundle, (url, properties) -> { throw new AssertionError("Must not connect"); },
                mock(WorkflowEventTriggerGuard.class), "validation");
    }

    @Test void invalidSslIsSanitizedAndCannotPass() throws Exception {
        assertFailure(environment(), bundle(), (url, properties) -> {
            throw new SQLException("password=private jdbc:postgresql://secret-host", "08006",
                    new javax.net.ssl.SSLHandshakeException("private body"));
        }, mock(WorkflowEventTriggerGuard.class), "connect_tls");
    }

    @Test void catalogDriftIsSanitizedAndRollsBack() throws Exception {
        Connection connection = connection("on");
        var guard = mock(WorkflowEventTriggerGuard.class);
        doThrow(new SQLException("private body password=private", "42501"))
                .when(guard).verifyReadOnly(any(), anyString());
        assertFailure(environment(), bundle(), (url, properties) -> connection, guard, "catalog_guard");
        verify(connection).rollback();
        verify(connection).close();
    }

    @ParameterizedTest @ValueSource(strings = {"rollback", "close"})
    void cleanupFailureCannotPass(String phase) throws Exception {
        Connection connection = connection("on");
        if (phase.equals("rollback")) doThrow(new SQLException("private body")).when(connection).rollback();
        else doThrow(new SQLException("private body")).when(connection).close();
        assertFailure(environment(), bundle(), (url, properties) -> connection, mock(WorkflowEventTriggerGuard.class), phase);
    }

    private void assertFailure(Map<String, String> environment, Path bundle, WorkflowReadOnlyPreflight.Connector connector,
                               WorkflowEventTriggerGuard guard, String phase) {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        assertThat(WorkflowLiveGuard.run(environment, bundle, connector, guard, new PrintStream(output), new PrintStream(errors)))
                .isEqualTo(1);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString().trim()).isEqualTo("LIVE_GUARD=FAIL phase=" + phase);
    }

    private Map<String, String> environment() throws Exception {
        Path ca = Files.writeString(temporary.resolve("ca.pem"), "fixture only");
        return new HashMap<>(Map.of("DB_HOST", "aws-0-region.pooler.supabase.com", "DB_PORT", "5432",
                "DB_NAME", "postgres", "DB_USER", "test-role.test-ref", "DB_PASSWORD", "test-only", "PGSSLROOTCERT", ca.toString()));
    }

    private Path bundle() throws Exception {
        Path bundle = Files.createTempDirectory(temporary, "bundle");
        for (String name : java.util.List.of("V3__create_workflow_persistence.sql", "V4__seed_workflow_definitions.sql")) {
            Files.copy(Path.of("src/main/resources/db/migration", name), bundle.resolve(name));
        }
        return bundle;
    }

    private Connection connection(String readOnly) throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn("postgres");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenAnswer(call -> {
            ResultSet rows = mock(ResultSet.class);
            when(rows.next()).thenReturn(true, false);
            if (call.<String>getArgument(0).contains("transaction_read_only")) when(rows.getString(1)).thenReturn(readOnly);
            return rows;
        });
        var prepared = mock(java.sql.PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(prepared);
        ResultSet schema = mock(ResultSet.class);
        when(schema.next()).thenReturn(true, false);
        when(prepared.executeQuery()).thenReturn(schema);
        return connection;
    }
}
