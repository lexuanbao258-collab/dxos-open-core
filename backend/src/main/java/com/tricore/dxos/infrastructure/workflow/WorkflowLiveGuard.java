package com.tricore.dxos.infrastructure.workflow;

import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Standalone catalog check: no Spring, Flyway, provisioning or workflow writer. */
public final class WorkflowLiveGuard {
    static final int SAMPLES = 5;

    private WorkflowLiveGuard() {}

    static Properties connectionProperties(Map<String, String> environment) {
        if (environment.containsKey("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE")) {
            throw new IllegalArgumentException("Local profile rejected");
        }
        Properties properties = WorkflowReadOnlyPreflight.connectionProperties(environment);
        properties.setProperty("readOnly", "true");
        properties.setProperty("readOnlyMode", "transaction");
        properties.setProperty("ApplicationName", "dxos-wf25-read-only-live-guard");
        return properties;
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        if (args.length != 1) {
            System.err.println("LIVE_GUARD=FAIL phase=validation");
            System.exit(1);
        }
        System.exit(run(System.getenv(), Path.of(args[0]), DriverManager::getConnection,
                WorkflowEventTriggerGuard.REVIEWED, System.out, System.err));
    }

    static int run(Map<String, String> environment, Path bundle, WorkflowReadOnlyPreflight.Connector connector,
                   WorkflowEventTriggerGuard guard, PrintStream output, PrintStream errors) {
        String phase = "validation";
        String backendTls = "NO_ROW";
        boolean schemaPresent = false;
        boolean transactionStarted = false;
        Connection connection = null;
        int exit = 0;
        double[] latency = new double[SAMPLES];
        try {
            Properties properties = connectionProperties(environment);
            WorkflowEventTriggerGuard.verifyBundle(bundle);
            String schema = WorkflowEventTriggerGuard.MANIFEST.path("schema").asText();
            String url = "jdbc:postgresql://" + environment.get("DB_HOST") + ":"
                    + environment.get("DB_PORT") + "/" + environment.get("DB_NAME");
            phase = "connect_tls";
            connection = connector.connect(url, properties);
            phase = "read_only";
            if (!connection.getAutoCommit()) throw new SQLException("Ambient transaction rejected");
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            transactionStarted = true;
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery("SELECT pg_catalog.current_setting('transaction_read_only')")) {
                    if (!rows.next() || !"on".equals(rows.getString(1))) throw new SQLException("Read-only rejected");
                }
            }
            phase = "database_identity";
            if (!environment.get("DB_NAME").equals(connection.getCatalog())) throw new SQLException("Database rejected");
            phase = "backend_tls";
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery("SELECT ssl FROM pg_catalog.pg_stat_ssl WHERE pid=pg_catalog.pg_backend_pid()")) {
                    if (rows.next()) {
                        boolean ssl = rows.getBoolean(1);
                        backendTls = rows.wasNull() ? "NULL" : ssl ? "TRUE" : "FALSE";
                    }
                }
            }
            phase = "catalog_guard";
            for (int sample = 0; sample < SAMPLES; sample++) {
                long start = System.nanoTime();
                guard.verifyReadOnly(connection, schema);
                latency[sample] = (System.nanoTime() - start) / 1_000_000.0;
            }
            phase = "schema_target";
            try (var statement = connection.prepareStatement("SELECT pg_catalog.to_regnamespace(?) IS NOT NULL")) {
                statement.setQueryTimeout(15);
                statement.setString(1, schema);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Schema evidence unavailable");
                    schemaPresent = rows.getBoolean(1);
                }
            }
            phase = "bundle";
            WorkflowEventTriggerGuard.verifyBundle(bundle);
        } catch (Exception | LinkageError failure) {
            errors.println("LIVE_GUARD=FAIL phase=" + phase);
            exit = 1;
        } finally {
            if (connection != null) {
                if (transactionStarted) {
                    try {
                        connection.rollback();
                    } catch (Exception failure) {
                        errors.println("LIVE_GUARD=FAIL phase=rollback");
                        exit = 1;
                    }
                }
                try {
                    connection.close();
                } catch (Exception failure) {
                    errors.println("LIVE_GUARD=FAIL phase=close");
                    exit = 1;
                }
            }
        }
        if (exit == 0) {
            boolean direct = environment.get("DB_HOST").endsWith(".supabase.co");
            output.println("LIVE_GUARD=PASS scope=READ_ONLY_CATALOG; transaction_read_only=on; connection_mode="
                    + (direct ? "DIRECT" : "SESSION_POOLER") + "; endpoint_port=" + environment.get("DB_PORT")
                    + "; " + (direct ? "client_direct_tls" : "client_pooler_tls") + "=VERIFIED_VERIFY_FULL");
            output.println("manifest_triggers=6; manifest_functions=8; bundle_sha256=PASS; schema_target="
                    + (schemaPresent ? "PRESENT" : "ABSENT") + "; "
                    + (direct ? "direct_backend_ssl" : "pooler_backend_ssl") + "=" + backendTls);
            double first = latency[0];
            double mean = Arrays.stream(latency).average().orElseThrow();
            Arrays.sort(latency);
            output.printf(Locale.ROOT, "catalog_guard_ms samples=%d first=%.3f min=%.3f median=%.3f mean=%.3f max=%.3f%n",
                    SAMPLES, first, latency[0], latency[SAMPLES / 2], mean, latency[SAMPLES - 1]);
            output.println("WRITE_READINESS=BLOCKED; migrator_runtime_roles=NOT_TESTED; postgres_is_not_production_runtime; backend_tls="
                    + ("TRUE".equals(backendTls) ? "ENCRYPTED_VERIFICATION_NOT_ATTESTED" : "UNACCEPTED"));
        }
        return exit;
    }
}
