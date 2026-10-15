package com.tricore.dxos.infrastructure.workflow;

import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Owner-approved empty-schema bootstrap only; no Spring, Flyway, role changes or cleanup. */
public final class WorkflowSchemaBootstrap {
    static final String HOST = "db.zsfctpnitkdztlfhzqgw.supabase.co";
    static final String URL = "jdbc:postgresql://" + HOST + ":5432/postgres";
    static final String SCHEMA = WorkflowSmokeRunner.SCHEMA;
    private WorkflowSchemaBootstrap() {}

    static Properties properties(Map<String, String> environment) {
        require(SCHEMA.equals(environment.get("WORKFLOW_SCHEMA_BOOTSTRAP_APPROVAL")), "Owner approval marker missing");
        require(HOST.equals(environment.get("DB_HOST")) && "5432".equals(environment.get("DB_PORT"))
                && "postgres".equals(environment.get("DB_NAME")) && "postgres".equals(environment.get("DB_USER")),
                "Approved Direct endpoint or identity mismatch");
        require(!environment.containsKey("WORKFLOW_DEV_LOCAL_DISPOSABLE") && !environment.containsKey("WF25_DISPOSABLE_TEST"),
                "Local profile rejected");
        return WorkflowLiveGuard.connectionProperties(environment);
    }

    record Audit(long oid, String owner, boolean empty, boolean historyAbsent, boolean privateAccess) {
        boolean valid() { return oid > 0 && "postgres".equals(owner) && empty && historyAbsent && privateAccess; }
    }

    record Result(String status, boolean direct, boolean tls, String postcommit, Audit audit,
                  boolean schemaChecked, String notification, String phase) {
        int report(PrintStream output) {
            output.println("SCHEMA_BOOTSTRAP=" + status);
            output.println("DIRECT_CONNECTION=" + (direct ? "PASS" : "FAIL"));
            output.println("TLS_VERIFIED=" + (tls ? "YES" : "NO"));
            output.println("POSTCOMMIT_AUDIT=" + postcommit);
            output.println("SCHEMA_OWNER=" + (audit == null ? schemaChecked ? "ABSENT" : "UNVERIFIED" : "postgres".equals(audit.owner()) ? "postgres" : "UNEXPECTED"));
            output.println("SCHEMA_EMPTY=" + (audit != null && audit.empty() ? "YES" : "NO"));
            output.println("FLYWAY_HISTORY=" + (audit == null ? schemaChecked ? "ABSENT" : "UNVERIFIED" : audit.historyAbsent() ? "ABSENT" : "PRESENT"));
            output.println("SCHEMA_PRESENT=" + (schemaChecked ? audit == null ? "NO" : "YES" : "UNVERIFIED"));
            output.println("OTHER_CLOUD_WRITES=" + ("NOT_EXECUTED".equals(status) || "PASS".equals(postcommit)
                    ? "NONE" : "UNVERIFIED_POSTCOMMIT_AUDIT_NOT_CONFIRMED"));
            output.println("PGRST_NOTIFY=" + notification);
            output.println("BOOTSTRAP_PHASE=" + phase);
            return "COMMITTED_PASS".equals(status) && "PASS".equals(postcommit) ? 0 : 1;
        }
    }

    static Result run(Map<String, String> environment, Path bundle, WorkflowReadOnlyPreflight.Connector connector,
                      WorkflowEventTriggerGuard guard) {
        String status = "NOT_EXECUTED", phase = "validation", notification = "NOT_OBSERVED";
        boolean direct = false, tls = false, writeStarted = false, commitAttempted = false, committed = false;
        Connection connection = null;
        Map<String, String> before = null;
        Audit expected = null;
        Properties properties;
        try {
            properties = properties(environment);
            WorkflowEventTriggerGuard.verifyBundle(bundle);
            phase = "connect_verify_full";
            connection = connector.connect(URL, properties);
            // LISTEN is a session-only read subscription, removed automatically when the connection closes.
            execute(connection, "LISTEN pgrst");
            beginReadOnly(connection);
            phase = "identity_tls_version";
            session(connection, true);
            direct = true; tls = true;
            phase = "precheck_catalog";
            guard.verifyReadOnly(connection, SCHEMA);
            precheck(connection);
            before = snapshot(connection);
            connection.rollback();
            connection.setAutoCommit(true);
            connection.setReadOnly(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            phase = "begin_approved_write";
            execute(connection, "BEGIN READ WRITE");
            writeStarted = true;
            connection.setAutoCommit(false);
            execute(connection, "SET LOCAL lock_timeout = '5s'");
            execute(connection, "SET LOCAL statement_timeout = '30s'");
            phase = "recheck_before_create";
            session(connection, false);
            precheck(connection);
            require(before.equals(snapshot(connection)), "Catalog changed before CREATE");
            WorkflowEventTriggerGuard.verifyBundle(bundle);
            phase = "create_schema";
            execute(connection, "CREATE SCHEMA dxos_wf25_smoke_01 AUTHORIZATION postgres");
            phase = "postcheck_before_commit";
            expected = audit(connection);
            require(expected != null && expected.valid(), "Private empty schema postcheck failed");
            guard.verify(connection, SCHEMA);
            require(before.equals(snapshot(connection)), "Unexpected catalog changes");
            WorkflowEventTriggerGuard.verifyBundle(bundle);
            phase = "commit";
            commitAttempted = true;
            connection.commit();
            committed = true;
            status = "COMMITTED_PASS";
            notification = notifications(connection);
        } catch (Exception failure) {
            if (writeStarted) {
                status = commitAttempted ? "COMMIT_UNKNOWN" : "ROLLED_BACK";
                try { if (connection != null) connection.rollback(); }
                catch (Exception rollbackFailure) { status = "COMMIT_UNKNOWN"; }
            } else if (connection != null) {
                try { if (!connection.getAutoCommit()) connection.rollback(); }
                catch (Exception ignored) { /* No CREATE was attempted. */ }
            }
        } finally {
            if (connection != null) try { connection.close(); }
            catch (Exception ignored) { /* Durable state is checked through a new connection below. */ }
        }

        Audit observed = null;
        boolean schemaChecked = false;
        String postcommit = "NOT_RUN";
        if (writeStarted) {
            postcommit = committed || commitAttempted ? "FAIL" : "NOT_RUN";
            try {
                properties = properties(environment);
                try (Connection fresh = connector.connect(URL, properties)) {
                    beginReadOnly(fresh);
                    try {
                        session(fresh, true);
                        guard.verifyReadOnly(fresh, SCHEMA);
                        observed = audit(fresh);
                        schemaChecked = true;
                        require(before.equals(snapshot(fresh)), "Catalog changed after transaction");
                        if (committed || commitAttempted) {
                            require(expected != null && observed != null && observed.valid()
                                    && expected.oid() == observed.oid(), "Durable schema not confirmed");
                            postcommit = "PASS";
                        } else require(observed == null, "Rollback not confirmed");
                    } finally { fresh.rollback(); }
                }
                if (committed) phase = "postcommit_audit_pass";
            } catch (Exception failure) {
                if (committed || commitAttempted) postcommit = "FAIL";
                phase = "reconciliation_failed";
            }
        }
        return new Result(status, direct, tls, postcommit, observed, schemaChecked, notification, phase);
    }

    static void beginReadOnly(Connection connection) throws SQLException {
        require(connection.getAutoCommit(), "Ambient transaction rejected");
        connection.setReadOnly(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setAutoCommit(false);
    }

    static void session(Connection connection, boolean readOnly) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery("""
                    SELECT current_user = 'postgres' AND session_user = 'postgres',
                           pg_catalog.current_database() = 'postgres',
                           pg_catalog.current_setting('server_version_num') = '170011',
                           pg_catalog.current_setting('transaction_read_only') = 'on',
                           (SELECT ssl FROM pg_catalog.pg_stat_ssl WHERE pid = pg_catalog.pg_backend_pid()),
                           pg_catalog.current_setting('fsync') = 'on',
                           pg_catalog.current_setting('full_page_writes') = 'on',
                           pg_catalog.current_setting('synchronous_commit') = 'on',
                           pg_catalog.pg_is_in_recovery() = false
                    """)) {
                require(rows.next() && rows.getBoolean(1) && rows.getBoolean(2) && rows.getBoolean(3)
                        && rows.getBoolean(4) == readOnly && rows.getBoolean(5) && rows.getBoolean(6)
                        && rows.getBoolean(7) && rows.getBoolean(8) && rows.getBoolean(9), "Session precheck failed");
            }
        }
    }

    private static void precheck(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery("""
                    SELECT pg_catalog.has_database_privilege(current_user, 'postgres', 'CREATE'),
                           pg_catalog.to_regnamespace('dxos_wf25_smoke_01') IS NULL,
                           NOT EXISTS (SELECT FROM pg_catalog.pg_default_acl d
                               JOIN pg_catalog.pg_roles r ON r.oid = d.defaclrole
                               WHERE r.rolname = 'postgres' AND d.defaclnamespace = 0),
                           (SELECT count(*) = 2 AND pg_catalog.bool_and(NOT rolcanlogin)
                            FROM pg_catalog.pg_roles
                            WHERE rolname IN ('dxos_wf25_migrator','dxos_wf25_runtime'))
                    """)) {
                require(rows.next() && rows.getBoolean(1) && rows.getBoolean(2) && rows.getBoolean(3)
                        && rows.getBoolean(4), "Schema, permissions or custom-role precheck failed");
            }
        }
    }

    static Audit audit(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery("""
                    SELECT n.oid, r.rolname,
                           NOT EXISTS (SELECT FROM pg_catalog.pg_class WHERE relnamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_proc WHERE pronamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_type WHERE typnamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_operator WHERE oprnamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_collation WHERE collnamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_conversion WHERE connamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_opclass WHERE opcnamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_opfamily WHERE opfnamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_ts_config WHERE cfgnamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_ts_dict WHERE dictnamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_ts_parser WHERE prsnamespace = n.oid)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_ts_template WHERE tmplnamespace = n.oid),
                           NOT EXISTS (SELECT FROM pg_catalog.pg_class
                               WHERE relnamespace = n.oid AND relname = 'flyway_schema_history'),
                           NOT EXISTS (SELECT FROM pg_catalog.aclexplode(
                               COALESCE(n.nspacl, pg_catalog.acldefault('n', n.nspowner))) a
                               WHERE a.grantee <> n.nspowner)
                           AND NOT EXISTS (SELECT FROM pg_catalog.pg_roles api
                               WHERE api.rolname IN ('anon','authenticated','service_role',
                                   'dxos_wf25_migrator','dxos_wf25_runtime')
                               AND (pg_catalog.has_schema_privilege(api.oid,n.oid,'USAGE')
                                 OR pg_catalog.has_schema_privilege(api.oid,n.oid,'CREATE')))
                    FROM pg_catalog.pg_namespace n JOIN pg_catalog.pg_roles r ON r.oid = n.nspowner
                    WHERE n.nspname = 'dxos_wf25_smoke_01'
                    """)) {
                if (!rows.next()) return null;
                var result = new Audit(rows.getLong(1), rows.getString(2), rows.getBoolean(3), rows.getBoolean(4), rows.getBoolean(5));
                require(!rows.next(), "Duplicate schema result");
                return result;
            }
        }
    }

    private static Map<String, String> snapshot(Connection connection) throws SQLException {
        Map<String, String> queries = Map.ofEntries(
                Map.entry("namespaces", "SELECT * FROM pg_catalog.pg_namespace WHERE nspname <> 'dxos_wf25_smoke_01'"),
                Map.entry("roles", "SELECT * FROM pg_catalog.pg_roles"),
                Map.entry("memberships", "SELECT * FROM pg_catalog.pg_auth_members"),
                Map.entry("defaults", "SELECT * FROM pg_catalog.pg_default_acl"),
                Map.entry("database_acl", "SELECT oid,datname,datdba,datacl,datallowconn,datconnlimit FROM pg_catalog.pg_database"),
                Map.entry("relations", "SELECT oid,relname,relnamespace,reltype,reloftype,relowner,relam,relpersistence,relkind,relchecks,relhasrules,relhastriggers,relrowsecurity,relforcerowsecurity,relispartition,relacl,reloptions,relpartbound FROM pg_catalog.pg_class"),
                Map.entry("routines", "SELECT * FROM pg_catalog.pg_proc"),
                Map.entry("types", "SELECT * FROM pg_catalog.pg_type"),
                Map.entry("attributes", "SELECT * FROM pg_catalog.pg_attribute"),
                Map.entry("constraints", "SELECT * FROM pg_catalog.pg_constraint"),
                Map.entry("triggers", "SELECT * FROM pg_catalog.pg_trigger"),
                Map.entry("events", "SELECT * FROM pg_catalog.pg_event_trigger"),
                Map.entry("dependencies", "SELECT * FROM pg_catalog.pg_depend WHERE NOT (classid='pg_catalog.pg_namespace'::pg_catalog.regclass AND objid=COALESCE(pg_catalog.to_regnamespace('dxos_wf25_smoke_01')::oid,0))"),
                Map.entry("shared_dependencies", "SELECT * FROM pg_catalog.pg_shdepend WHERE NOT (classid='pg_catalog.pg_namespace'::pg_catalog.regclass AND objid=COALESCE(pg_catalog.to_regnamespace('dxos_wf25_smoke_01')::oid,0) AND dbid=(SELECT oid FROM pg_catalog.pg_database WHERE datname=pg_catalog.current_database()))"));
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : queries.entrySet()) {
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery("SELECT pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to("
                        + "COALESCE(pg_catalog.jsonb_agg(pg_catalog.to_jsonb(x) ORDER BY pg_catalog.to_jsonb(x)::text),'[]'::jsonb)::text,'UTF8')),'hex') FROM ("
                        + entry.getValue() + ") x")) {
                    require(rows.next(), "Catalog snapshot unavailable");
                    result.put(entry.getKey(), rows.getString(1));
                }
            }
        }
        return result;
    }

    private static String notifications(Connection connection) {
        try {
            Class<?> type = Class.forName("org.postgresql.PGConnection");
            Object postgres = connection.unwrap(type);
            int pid = (int) type.getMethod("getBackendPID").invoke(postgres);
            Object[] notifications = (Object[]) type.getMethod("getNotifications", int.class).invoke(postgres, 1000);
            Class<?> notice = Class.forName("org.postgresql.PGNotification");
            if (notifications != null) for (Object notification : notifications) {
                if ("pgrst".equals(notice.getMethod("getName").invoke(notification))
                        && "reload schema".equals(notice.getMethod("getParameter").invoke(notification))
                        && pid == (int) notice.getMethod("getPID").invoke(notification)) return "OBSERVED_OWN_SESSION";
            }
            return "NOT_OBSERVED";
        } catch (Exception failure) { return "UNVERIFIED"; }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.setQueryTimeout(15); statement.execute(sql); }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        Result result;
        try {
            require(args.length == 1, "Bundle path required");
            result = run(System.getenv(), Path.of(args[0]), DriverManager::getConnection, WorkflowEventTriggerGuard.REVIEWED);
        } catch (Exception failure) {
            result = new Result("NOT_EXECUTED", false, false, "NOT_RUN", null, false, "NOT_OBSERVED", "arguments");
        }
        System.exit(result.report(System.out));
    }
}
