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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class WorkflowRuntimePrivilegeAuditTest {
    static final Path SQL = Path.of("../docs/evidence/issue-25-runtime-privilege-review.sql");
    @TempDir Path temporary;

    @Test void reviewedSqlContainsFifteenCatalogQueriesAndNoExecutableTransactionCommands() throws Exception {
        var queries = WorkflowRuntimePrivilegeAudit.queries(SQL);
        assertThat(queries).hasSize(15).allMatch(query -> query.startsWith("SELECT ") || query.startsWith("WITH RECURSIVE "));
        assertThat(queries).noneMatch(query -> query.contains("pg_catalog.set_config") || query.contains("pg_read_file("));
    }

    @ParameterizedTest @ValueSource(strings = {"\nCREATE ROLE unsafe;", "\nSELECT pg_catalog.pg_advisory_lock(1);",
            "\nWITH x AS (DELETE FROM secret RETURNING *) SELECT * FROM x;"})
    void changedSqlIsRejectedBeforeConnecting(String change) throws Exception {
        Path altered = Files.writeString(temporary.resolve("audit.sql"), Files.readString(SQL) + change);
        assertFailure(environment(), altered, (url, properties) -> { throw new AssertionError("Must not connect"); }, "validation");
    }

    @Test void reportsMissingRolesAndPublicTempWithoutExposingIdentifiersOrSecrets() throws Exception {
        Connection connection = connection("on");
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int exit = WorkflowRuntimePrivilegeAudit.run(environment(), SQL, (url, properties) -> {
            assertThat(properties.getProperty("sslmode")).isEqualTo("verify-full");
            assertThat(properties.getProperty("sslrootcert")).endsWith("ca.pem");
            assertThat(properties.getProperty("currentSchema")).isEqualTo("pg_catalog,pg_temp");
            assertThat(properties.getProperty("options")).isEqualTo("-c default_transaction_read_only=on");
            assertThat(properties.getProperty("readOnly")).isEqualTo("true");
            assertThat(properties.getProperty("readOnlyMode")).isEqualTo("transaction");
            assertThat(properties.getProperty("sslfactory")).isNull();
            assertThat(properties.getProperty("sslhostnameverifier")).isNull();
            return connection;
        }, new PrintStream(output), new PrintStream(errors));
        assertThat(exit).isZero();
        assertThat(errors.toString()).isEmpty();
        assertThat(output.toString()).contains("RUNTIME_PRIVILEGE_AUDIT=PASS", "transaction_read_only=on",
                "\"dxos_wf25_runtime\":\"NOT_PROVISIONED\"", "\"public_temp\":true", "WRITE_READINESS=BLOCKED")
                .doesNotContain("private-secret", "jdbc:postgresql:", "postgres.fixture-ref", "password=");
        var order = inOrder(connection);
        order.verify(connection).setReadOnly(true);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).rollback();
        order.verify(connection).close();
        verify(connection, never()).commit();
        Statement statement = connection.createStatement();
        verify(statement, times(16)).executeQuery(anyString());
        verify(statement, never()).execute(anyString());
        verify(statement, never()).executeUpdate(anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"off", "", "ON"})
    void serverReadOnlyMustBeConfirmedBeforeCatalog(String mode) throws Exception {
        Connection connection = connection(mode);
        assertFailure(environment(), SQL, (url, properties) -> connection, "read_only");
        verify(connection.createStatement(), times(1)).executeQuery(anyString());
        verify(connection).rollback();
        verify(connection).close();
    }

    @ParameterizedTest @ValueSource(strings = {"true", "false", ""})
    void anyLocalBypassIsRejectedBeforeConnect(String value) throws Exception {
        var environment = environment();
        environment.put("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE", value);
        assertFailure(environment, SQL, (url, properties) -> { throw new AssertionError("Must not connect"); }, "validation");
    }

    @Test void driverErrorsCannotExposeConnectionOrPassword() throws Exception {
        assertFailure(environment(), SQL, (url, properties) -> {
            throw new SQLException("password=private-secret jdbc:postgresql://private-secret", "08006");
        }, "connect_tls");
    }

    @Test void catalogFailureDiscardsPartialReportAndRollsBack() throws Exception {
        Connection connection = connection("on");
        Statement statement = connection.createStatement();
        doThrow(new SQLException("private-secret password=private-secret"))
                .when(statement).executeQuery(argThat(sql -> sql.contains("SELECT granted.rolname")));
        assertFailure(environment(), SQL, (url, properties) -> connection, "memberships");
        verify(connection).rollback();
        verify(connection).close();
    }

    @Test void presentRolesAndEffectiveColumnGrantOptionsStillRequireManualReview() throws Exception {
        Connection connection = connection("on");
        Statement statement = connection.createStatement();
        doAnswer(call -> rows(List.of(Map.of("name", "postgres", "status", "PRESENT"),
                Map.of("name", "dxos_wf25_migrator", "status", "PRESENT"),
                Map.of("name", "dxos_wf25_runtime", "status", "PRESENT"))))
                .when(statement).executeQuery(argThat(sql -> sql.contains("SELECT wanted.name")));
        doAnswer(call -> rows(List.of(Map.of("rolname", "dxos_wf25_runtime", "attname", "private-secret",
                "privilege", "REFERENCES", "effective", true, "grantable", true))))
                .when(statement).executeQuery(argThat(sql -> sql.contains("SELECT r.rolname, c.relname, a.attname")));
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        assertThat(WorkflowRuntimePrivilegeAudit.run(environment(), SQL, (url, properties) -> connection,
                new PrintStream(output), new PrintStream(errors))).isZero();
        assertThat(errors.toString()).isEmpty();
        assertThat(output.toString()).contains("PRESENT_REVIEW_REQUIRED", "dxos_wf25_runtime:REFERENCES",
                "\"effective\":1", "\"grantable\":1").doesNotContain("private-secret");
    }

    @Test void publicSchemaCreateIsReportedBeforeRolesExist() throws Exception {
        Connection connection = connection("on");
        Statement statement = connection.createStatement();
        doAnswer(call -> rows(List.of(Map.of("rolname", "PUBLIC", "nspname", "private-secret", "create", true,
                "create_grantable", false, "usage", true))))
                .when(statement).executeQuery(argThat(sql -> sql.contains("SELECT r.rolname, n.nspname")));
        var output = new ByteArrayOutputStream();
        assertThat(WorkflowRuntimePrivilegeAudit.run(environment(), SQL, (url, properties) -> connection,
                new PrintStream(output), new PrintStream(new ByteArrayOutputStream()))).isZero();
        assertThat(output.toString()).contains("\"public_schema_create_count\":1", "NOT_PROVISIONED")
                .doesNotContain("private-secret");
    }

    @Test void publicSecurityDefinerCallabilityIsReportedBeforeRolesExist() throws Exception {
        Connection connection = connection("on");
        Statement statement = connection.createStatement();
        doAnswer(call -> rows(List.of(Map.of("rolname", "PUBLIC", "routine", "private-secret",
                "owner", "private-secret", "prosecdef", true, "execute", true, "grantable", false))))
                .when(statement).executeQuery(argThat(sql -> sql.contains("SELECT r.rolname, p.oid::")));
        var output = new ByteArrayOutputStream();
        assertThat(WorkflowRuntimePrivilegeAudit.run(environment(), SQL, (url, properties) -> connection,
                new PrintStream(output), new PrintStream(new ByteArrayOutputStream()))).isZero();
        assertThat(output.toString()).contains("PUBLIC:all", "\"prosecdef\":1", "NOT_PROVISIONED")
                .doesNotContain("private-secret");
    }

    @ParameterizedTest @ValueSource(strings = {"rollback", "close"})
    void cleanupFailurePreventsSuccessReport(String phase) throws Exception {
        Connection connection = connection("on");
        if (phase.equals("rollback")) doThrow(new SQLException("private-secret")).when(connection).rollback();
        else doThrow(new SQLException("private-secret")).when(connection).close();
        assertFailure(environment(), SQL, (url, properties) -> connection, phase);
    }

    private void assertFailure(Map<String, String> environment, Path sql, WorkflowReadOnlyPreflight.Connector connector,
                               String phase) {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        assertThat(WorkflowRuntimePrivilegeAudit.run(environment, sql, connector, new PrintStream(output), new PrintStream(errors)))
                .isEqualTo(1);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString().trim()).isEqualTo("RUNTIME_PRIVILEGE_AUDIT=FAIL phase=" + phase);
    }

    private Map<String, String> environment() throws Exception {
        Path ca = Files.writeString(temporary.resolve("ca.pem"), "fixture only");
        return new HashMap<>(Map.of("DB_HOST", "aws-0-fixture.pooler.supabase.com", "DB_PORT", "5432", "DB_NAME", "postgres",
                "DB_USER", "postgres.fixture-ref", "DB_PASSWORD", "private-secret", "PGSSLROOTCERT", ca.toString()));
    }

    private Connection connection(String mode) throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn("postgres");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.startsWith("SELECT pg_catalog.current_setting('transaction_read_only')")) {
                ResultSet rows = mock(ResultSet.class);
                when(rows.next()).thenReturn(true, false);
                when(rows.getString(1)).thenReturn(mode);
                when(rows.getString(2)).thenReturn("pg_catalog,pg_temp");
                when(rows.getString(3)).thenReturn("postgres");
                when(rows.getString(4)).thenReturn("postgres");
                return rows;
            }
            if (sql.contains("AS server_version")) return rows(List.of(Map.of("server_version", "17.11", "backend_ssl", false,
                    "database", "private-secret", "search_path", "private-secret")));
            if (sql.contains("SELECT wanted.name")) return rows(List.of(
                    Map.of("name", "postgres", "status", "PRESENT"),
                    Map.of("name", "dxos_wf25_migrator", "status", "NOT_PROVISIONED"),
                    Map.of("name", "dxos_wf25_runtime", "status", "NOT_PROVISIONED")));
            if (sql.contains("a.grantee = 0 ORDER BY")) return rows(List.of(Map.of("datname", "private-secret", "privilege_type", "TEMPORARY", "is_grantable", false)));
            return rows(List.of());
        });
        return connection;
    }

    private ResultSet rows(List<Map<String, Object>> data) throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        var labels = data.isEmpty() ? List.<String>of() : new ArrayList<>(data.getFirst().keySet());
        AtomicInteger row = new AtomicInteger(-1);
        when(rows.next()).thenAnswer(call -> row.incrementAndGet() < data.size());
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(labels.size());
        for (int column = 1; column <= labels.size(); column++) {
            String label = labels.get(column - 1);
            when(metadata.getColumnLabel(column)).thenReturn(label);
            when(rows.getObject(column)).thenAnswer(call -> data.get(row.get()).get(label));
            when(rows.getObject(label)).thenAnswer(call -> data.get(row.get()).get(label));
            when(rows.getString(label)).thenAnswer(call -> data.get(row.get()).get(label));
        }
        return rows;
    }
}
