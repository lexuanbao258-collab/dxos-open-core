package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowSecurityDefinerReviewTest {
    static final Path SQL = Path.of("../docs/evidence/issue-25-security-definer-review.sql");
    @TempDir Path temporary;

    @Test void pinnedSqlIncludesBoundHelpersAndNeverReturnsBodiesOrArbitraryConfiguration() throws Exception {
        var queries = WorkflowSecurityDefinerReview.queries(SQL);
        assertThat(queries).hasSize(8).allMatch(query -> query.startsWith("SELECT ") || query.startsWith("WITH RECURSIVE "));
        assertThat(queries.get(3)).contains("AS function_search_path", "AS definition_sha256", "AS configuration_sha256",
                "CASE WHEN r.oid IS NOT NULL", "p.oid = ANY (?::pg_catalog.oid[])")
                .doesNotContain("AS definition,", "p.prosrc", "p.probin", "p.proconfig AS");
        assertThat(queries.getLast()).isEqualTo("SELECT pg_catalog.to_regprocedure(?)::pg_catalog.oid AS helper_oid");
    }

    @ParameterizedTest @ValueSource(strings = {"\nCREATE ROLE unsafe;", "\nSELECT pg_catalog.pg_advisory_lock(1);",
            "\nWITH x AS (DELETE FROM secret RETURNING *) SELECT * FROM x;"})
    void modifiedSqlStopsBeforeConnecting(String mutation) throws Exception {
        Path changed = Files.writeString(temporary.resolve("changed.sql"), Files.readString(SQL) + mutation);
        assertFailure(Map.of(), changed, (url, properties) -> { throw new AssertionError("Must not connect"); }, "validation");
    }

    @Test void capturesUnknownPrivilegesAsNullAndPublishesOnlyAfterCleanup() throws Exception {
        Path directory = protectedDirectory(temporary);
        Connection connection = connection("on", routine());
        String output = capture(environment(directory), connection);
        String shared = Files.readString(directory.resolve("shareable-metadata.json"));
        assertThat(shared).contains("\"runtime_execute\" : null", "UNKNOWN_NOT_PROVISIONED", "NEEDS_REVIEW",
                "\"potential_public_direct\" : true", "\"backend_ssl\" : false")
                .doesNotContain("private-body-secret", "private-config-secret", "fixture-password", "fixture-ref", "jdbc:postgresql:");
        assertThat(directory.resolve("private-function-review.json")).doesNotExist();
        assertThat(output).contains("SECURITY_DEFINER_CAPTURE=PASS", "WRITE_READINESS=BLOCKED").doesNotContain("fixture-password");
        var order = inOrder(connection);
        order.verify(connection).setReadOnly(true);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).rollback();
        order.verify(connection).close();
        verify(connection, never()).commit();
    }

    @ParameterizedTest @ValueSource(strings = {"off", "ON", ""})
    void unconfirmedReadOnlyStopsBeforeRoutineDiscovery(String mode) throws Exception {
        Path directory = protectedDirectory(temporary);
        Connection connection = connection(mode, routine());
        assertFailure(environment(directory), SQL, (url, properties) -> connection, "session");
        verify(connection, times(1)).prepareStatement(anyString());
        verify(connection).rollback();
        try (var files = Files.list(directory)) { assertThat(files.toList()).isEmpty(); }
    }

    @Test void falseForUnprovisionedRuntimeIsRejectedRatherThanPublished() throws Exception {
        Path directory = protectedDirectory(temporary);
        var routine = routine();
        routine.put("runtime_execute", false);
        Connection connection = connection("on", routine);
        assertFailure(environment(directory), SQL, (url, properties) -> connection, "evidence");
        assertThat(directory.resolve("shareable-metadata.json")).doesNotExist();
    }

    @Test void unexpectedPrivateColumnsAreRejected() throws Exception {
        Path directory = protectedDirectory(temporary);
        var routine = routine();
        routine.put("definition", "private-body-secret");
        Connection connection = connection("on", routine);
        assertFailure(environment(directory), SQL, (url, properties) -> connection, "routines");
    }

    @ParameterizedTest @ValueSource(strings = {"rollback", "close"})
    void cleanupFailurePreventsArtifactsAndSuccess(String phase) throws Exception {
        Path directory = protectedDirectory(temporary);
        Connection connection = connection("on", routine());
        if (phase.equals("rollback")) doThrow(new SQLException("private-body-secret")).when(connection).rollback();
        else doThrow(new SQLException("fixture-password")).when(connection).close();
        assertFailure(environment(directory), SQL, (url, properties) -> connection, phase);
        assertThat(directory.resolve("shareable-metadata.json")).doesNotExist();
        assertThat(directory.resolve("private-function-review.json")).doesNotExist();
    }

    @Test void driftStopsBeforeFetchingPrivateBodies() throws Exception {
        Path first = protectedDirectory(temporary);
        capture(environment(first), connection("on", routine()));
        Path second = protectedDirectory(temporary);
        var environment = environment(second);
        environment.put("WORKFLOW_DEFINER_METADATA_FILE", first.resolve("shareable-metadata.json").toString());
        var changed = routine();
        changed.put("definition_sha256", "c".repeat(64));
        Connection connection = connection("on", changed);
        assertFailure(environment, SQL, (url, properties) -> connection, "baseline");
        verify(connection, never()).prepareStatement(argThat(query -> query.contains("CASE WHEN ? THEN pg_catalog.pg_get_functiondef")));
        assertThat(second.resolve("private-function-review.json")).doesNotExist();
    }

    @Test void tamperedBaselineStopsBeforeConnect() throws Exception {
        Path first = protectedDirectory(temporary);
        capture(environment(first), connection("on", routine()));
        Path metadata = first.resolve("shareable-metadata.json");
        Files.writeString(metadata, Files.readString(metadata).replace("\"backend_ssl\" : false", "\"backend_ssl\" : true"));
        var environment = environment(protectedDirectory(temporary));
        environment.put("WORKFLOW_DEFINER_METADATA_FILE", metadata.toString());
        assertFailure(environment, SQL, (url, properties) -> { throw new AssertionError("Must not connect"); }, "validation");
    }

    @Test void driverErrorsAndLocalBypassCannotExposeSecrets() throws Exception {
        var environment = environment(protectedDirectory(temporary));
        assertFailure(environment, SQL, (url, properties) -> {
            throw new SQLException("fixture-password jdbc:postgresql://private-body-secret");
        }, "connect_tls");
        environment.put("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE", "false");
        assertFailure(environment, SQL, (url, properties) -> { throw new AssertionError("Must not connect"); }, "validation");
    }

    private String capture(Map<String, String> environment, Connection connection) {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int exit = WorkflowSecurityDefinerReview.run(environment, SQL, (url, properties) -> {
            assertThat(properties.getProperty("sslmode")).isEqualTo("verify-full");
            assertThat(properties.getProperty("currentSchema")).isEqualTo("pg_catalog,pg_temp");
            assertThat(properties.getProperty("readOnly")).isEqualTo("true");
            assertThat(properties.getProperty("options")).isEqualTo("-c default_transaction_read_only=on");
            assertThat(properties.getProperty("sslfactory")).isNull();
            return connection;
        }, new PrintStream(output), new PrintStream(errors));
        assertThat(exit).as(errors.toString()).isZero();
        assertThat(errors.toString()).isEmpty();
        return output.toString();
    }

    private void assertFailure(Map<String, String> environment, Path sql, WorkflowReadOnlyPreflight.Connector connector, String phase) {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        assertThat(WorkflowSecurityDefinerReview.run(environment, sql, connector, new PrintStream(output), new PrintStream(errors)))
                .isEqualTo(1);
        assertThat(output.toString()).isEmpty();
        assertThat(errors.toString().trim()).isEqualTo("SECURITY_DEFINER_CAPTURE=FAIL phase=" + phase);
    }

    private Map<String, String> environment(Path directory) throws Exception {
        Path ca = Files.writeString(temporary.resolve("ca.pem"), "fixture only");
        return new HashMap<>(Map.of("DB_HOST", "aws-0-fixture.pooler.supabase.com", "DB_PORT", "5432", "DB_NAME", "postgres",
                "DB_USER", "postgres.fixture-ref", "DB_PASSWORD", "fixture-password", "PGSSLROOTCERT", ca.toString(),
                "WORKFLOW_DEFINER_REVIEW_DIR", directory.toString()));
    }

    static Path protectedDirectory(Path root) throws Exception {
        Path directory = Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
        if (System.getProperty("os.name").startsWith("Windows")) {
            String user = System.getenv("USERDOMAIN") + "\\" + System.getenv("USERNAME");
            Files.setOwner(directory, directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(user));
            var process = new ProcessBuilder("icacls.exe", directory.toString(), "/inheritance:r", "/grant:r", user + ":(OI)(CI)F",
                    "/grant:r", "*S-1-5-18:(OI)(CI)F").redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            assertThat(process.waitFor()).isZero();
        } else Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        return directory;
    }

    private Map<String, Object> routine() {
        var routine = new TreeMap<String, Object>();
        routine.putAll(Map.of("oid", 123L, "signature", "private_schema.demo()", "security_definer", true,
                "definition_sha256", "a".repeat(64), "configuration_sha256", "b".repeat(64), "runtime_exists", false,
                "public_execute", true, "public_schema_usage", true, "call_context", "ORDINARY_CALL"));
        for (String label : List.of("runtime_execute", "runtime_schema_usage", "runtime_inherits_owner", "runtime_can_set_owner"))
            routine.put(label, null);
        return routine;
    }

    private Connection connection(String mode, Map<String, Object> routine) throws Exception {
        var queries = WorkflowSecurityDefinerReview.queries(SQL);
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn("postgres");
        when(connection.createArrayOf(eq("oid"), any(Object[].class))).thenAnswer(call -> mock(java.sql.Array.class));
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String query = call.getArgument(0);
            PreparedStatement statement = mock(PreparedStatement.class);
            List<Map<String, Object>> data = query.equals(queries.getFirst()) ? List.of(Map.of("server_version", "17.11",
                    "transaction_read_only", mode, "search_path", "pg_catalog,pg_temp", "session_user", "postgres",
                    "current_user", "postgres", "database_name", "postgres", "backend_ssl", false))
                    : query.equals(queries.get(1)) ? List.of(Map.of("name", "dxos_wf25_runtime", "status", "NOT_PROVISIONED"),
                    Map.of("name", "dxos_wf25_migrator", "status", "NOT_PROVISIONED"))
                    : query.equals(queries.get(3)) ? List.of(routine) : List.of();
            when(statement.executeQuery()).thenAnswer(ignored -> rows(data));
            return statement;
        });
        return connection;
    }

    private ResultSet rows(List<Map<String, Object>> data) throws Exception {
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        var labels = data.isEmpty() ? List.<String>of() : new ArrayList<>(data.getFirst().keySet());
        AtomicInteger index = new AtomicInteger(-1);
        when(result.next()).thenAnswer(call -> index.incrementAndGet() < data.size());
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(labels.size());
        for (int column = 1; column <= labels.size(); column++) {
            String label = labels.get(column - 1);
            when(metadata.getColumnLabel(column)).thenReturn(label);
            when(result.getObject(column)).thenAnswer(call -> data.get(index.get()).get(label));
        }
        return result;
    }
}
