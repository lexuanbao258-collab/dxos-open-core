package com.tricore.dxos.infrastructure.workflow;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Catalog-only entry point; never starts Spring, Flyway or write smoke. */
public final class WorkflowReadOnlyPreflight {
    private static final String SCHEMA = WorkflowSmokeRunner.SCHEMA;
    private static final List<String> REQUIRED = List.of("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD", "PGSSLROOTCERT");
    private String phase = "validation";
    private boolean transactionStarted;

    static Properties connectionProperties(Map<String, String> environment) {
        var missing = REQUIRED.stream().filter(key -> environment.get(key) == null || environment.get(key).isBlank()).toList();
        if (!missing.isEmpty()) throw new IllegalArgumentException("Missing environment variables: " + String.join(", ", missing));
        if (!(environment.get("DB_HOST").matches("db\\.[a-z0-9]{10,40}\\.supabase\\.co")
                || environment.get("DB_HOST").matches("[a-z0-9][a-z0-9.-]*\\.pooler\\.supabase\\.com"))
                || !"5432".equals(environment.get("DB_PORT"))) {
            throw new IllegalArgumentException("Supabase endpoint rejected");
        }
        if (!environment.get("DB_NAME").matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Database identifier rejected");
        try {
            if (!Files.isRegularFile(Path.of(environment.get("PGSSLROOTCERT")))) throw new IllegalArgumentException("CA certificate missing");
        } catch (java.nio.file.InvalidPathException failure) {
            throw new IllegalArgumentException("CA certificate missing");
        }
        Properties properties = new Properties();
        properties.setProperty("user", environment.get("DB_USER"));
        properties.setProperty("password", environment.get("DB_PASSWORD"));
        properties.setProperty("sslmode", "verify-full");
        properties.setProperty("sslrootcert", environment.get("PGSSLROOTCERT"));
        properties.setProperty("currentSchema", SCHEMA + ",pg_catalog,pg_temp");
        properties.setProperty("options", "-c default_transaction_read_only=on");
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "20");
        properties.setProperty("ApplicationName", "dxos-wf25-read-only-preflight");
        return properties;
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        System.exit(run(System.getenv(), DriverManager::getConnection, System.out, System.err));
    }

    @FunctionalInterface
    interface Connector {
        Connection connect(String url, Properties properties) throws SQLException;
    }

    @FunctionalInterface
    interface CatalogReview {
        void inspect(Connection connection) throws Exception;
    }

    static int run(Map<String, String> environment, Connector connector, PrintStream output, PrintStream errors) {
        return run(environment, connector, output, errors, connection -> {});
    }

    static int run(Map<String, String> environment, Connector connector, PrintStream output, PrintStream errors,
                   CatalogReview review) {
        var preflight = new WorkflowReadOnlyPreflight();
        Connection connection = null;
        int exit = 0;
        try {
            Properties properties = connectionProperties(environment);
            String url = "jdbc:postgresql://" + environment.get("DB_HOST") + ":" + environment.get("DB_PORT") + "/" + environment.get("DB_NAME");
            preflight.phase = "connect";
            connection = connector.connect(url, properties);
            preflight.beginReadOnly(connection);
            boolean direct = environment.get("DB_HOST").endsWith(".supabase.co");
            output.println((direct ? "client_direct_tls" : "client_pooler_tls") + "=VERIFIED_VERIFY_FULL");
            preflight.phase = "backend_tls";
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery("SELECT ssl FROM pg_catalog.pg_stat_ssl WHERE pid=pg_catalog.pg_backend_pid()")) {
                    if (!rows.next()) {
                        output.println((direct ? "direct_backend_ssl" : "pooler_backend_ssl") + "=NO_ROW");
                    } else {
                        boolean ssl = rows.getBoolean(1);
                        output.println((direct ? "direct_backend_ssl" : "pooler_backend_ssl") + "="
                                + (rows.wasNull() ? "NULL" : ssl ? "TRUE" : "FALSE"));
                    }
                }
            }
            preflight.phase = "database_identity";
            if (!environment.get("DB_NAME").equals(connection.getCatalog())) throw new SQLException("Database identity mismatch");
            preflight.inspectCatalog(connection, output);
            preflight.phase = "event_review";
            review.inspect(connection);
        } catch (Exception failure) {
            preflight.failure(errors, failure);
            exit = 1;
        } finally {
            if (connection != null) {
                if (preflight.transactionStarted) {
                    preflight.phase = "rollback";
                    try {
                        connection.rollback();
                    } catch (Exception failure) {
                        preflight.failure(errors, failure);
                        exit = 1;
                    }
                }
                preflight.phase = "close";
                try {
                    connection.close();
                } catch (Exception failure) {
                    preflight.failure(errors, failure);
                    exit = 1;
                }
            }
        }
        if (exit == 0) output.println("READ_ONLY_CATALOG_COMPLETE; role suitability and write approval require review");
        return exit;
    }

    private void failure(PrintStream errors, Exception failure) {
        String reportedPhase = phase;
        if (failure instanceof SQLException sqlFailure) {
            // TLS handshake errors are raised during DriverManager.connect, before a connection exists.
            if (phase.equals("connect")) {
                Throwable cause = failure.getCause();
                for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
                    if (cause instanceof javax.net.ssl.SSLException || cause instanceof java.security.cert.CertificateException) {
                        reportedPhase = "tls_handshake";
                        break;
                    }
                    if (cause.getCause() == cause) break;
                }
            }
            String state = sqlFailure.getSQLState();
            if (state == null || !state.matches("[A-Z0-9]{5}")) state = "UNKNOWN";
            errors.println("JDBC_PREFLIGHT_FAIL phase=" + reportedPhase + " sqlstate=" + state);
        } else {
            String category = failure instanceof SecurityException ? "access_denied"
                    : failure instanceof IllegalArgumentException ? (phase.equals("validation") ? "invalid_configuration" : "invalid_argument")
                    : failure instanceof RuntimeException ? "unexpected_runtime" : "unexpected_exception";
            errors.println("JDBC_PREFLIGHT_FAIL phase=" + phase + " error=" + category);
        }
    }

    private void beginReadOnly(Connection connection) throws SQLException {
        phase = "transaction";
        if (!connection.getAutoCommit()) throw new SQLException("Ambient transaction rejected");
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        transactionStarted = true;
        phase = "read_only";
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery("SELECT pg_catalog.current_setting('transaction_read_only')")) {
                if (!rows.next() || !"on".equals(rows.getString(1))) throw new SQLException("Verified read-only transaction required");
            }
        }
    }

    static void inspect(Connection connection, PrintStream output) throws SQLException {
        var preflight = new WorkflowReadOnlyPreflight();
        Exception primary = null;
        try {
            preflight.beginReadOnly(connection);
            preflight.inspectCatalog(connection, output);
        } catch (SQLException | RuntimeException failure) {
            primary = failure;
            throw failure;
        } finally {
            if (preflight.transactionStarted) {
                try {
                    connection.rollback();
                } catch (SQLException | RuntimeException cleanupFailure) {
                    if (primary == null) throw cleanupFailure;
                    primary.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    private void inspectCatalog(Connection connection, PrintStream output) throws SQLException {
        phase = "schema";
        {
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery("""
                        SELECT pg_catalog.current_setting('transaction_read_only'),
                               pg_catalog.current_setting('search_path'),
                               pg_catalog.to_regnamespace('dxos_wf25_smoke_01') IS NOT NULL,
                               pg_catalog.current_schema()
                        """)) {
                    if (!rows.next() || !"on".equals(rows.getString(1))
                            || !(SCHEMA + ",pg_catalog,pg_temp").equals(rows.getString(2).replace(" ", ""))
                            || (rows.getBoolean(3) && !SCHEMA.equals(rows.getString(4)))) {
                        throw new SQLException("Read-only schema session rejected");
                    }
                    output.println("transaction_read_only=on; expected_schema_present=" + rows.getBoolean(3));
                }
            }
            for (var query : List.of(
                    Map.entry("identity", """
                        SELECT pg_catalog.version() AS version,pg_catalog.current_database() AS database,
                        current_user AS current_role,session_user AS login_role,pg_catalog.current_schema() AS current_schema,
                        pg_catalog.current_setting('search_path') AS search_path,pg_catalog.pg_is_in_recovery() AS in_recovery
                        """),
                    Map.entry("settings", """
                        SELECT name,setting,source FROM pg_catalog.pg_settings WHERE name IN
                        ('server_version','server_version_num','default_transaction_read_only','default_transaction_isolation',
                        'transaction_isolation','session_replication_role','fsync','full_page_writes','synchronous_commit','ssl','search_path')
                        ORDER BY name
                        """),
                    Map.entry("schema", """
                        SELECT n.nspname,r.rolname AS owner,n.nspacl,
                        pg_catalog.has_schema_privilege(current_user,n.oid,'USAGE') AS usage,
                        pg_catalog.has_schema_privilege(current_user,n.oid,'CREATE') AS can_create
                        FROM pg_catalog.pg_namespace n JOIN pg_catalog.pg_roles r ON r.oid=n.nspowner
                        WHERE n.nspname='dxos_wf25_smoke_01'
                        """),
                    Map.entry("database_privileges", """
                        SELECT pg_catalog.has_database_privilege(current_user,pg_catalog.current_database(),'CREATE') AS can_create_schema,
                        pg_catalog.has_database_privilege(current_user,pg_catalog.current_database(),'TEMP') AS can_create_temp,
                        pg_catalog.has_language_privilege(current_user,'plpgsql','USAGE') AS plpgsql_usage
                        """),
                    Map.entry("roles", """
                        SELECT rolname,rolsuper,rolcreatedb,rolcreaterole,rolinherit,rolcanlogin,rolbypassrls,
                        pg_catalog.pg_has_role(current_user,oid,'MEMBER') AS member,
                        pg_catalog.pg_has_role(current_user,oid,'USAGE') AS inherits FROM pg_catalog.pg_roles ORDER BY rolname
                        """),
                    Map.entry("memberships", """
                        SELECT parent.rolname AS granted_role,member.rolname AS member,m.admin_option
                        FROM pg_catalog.pg_auth_members m JOIN pg_catalog.pg_roles parent ON parent.oid=m.roleid
                        JOIN pg_catalog.pg_roles member ON member.oid=m.member ORDER BY parent.rolname,member.rolname
                        """),
                    Map.entry("schema_create_privileges", """
                        SELECT n.nspname,r.rolname AS owner,n.nspacl FROM pg_catalog.pg_namespace n
                        JOIN pg_catalog.pg_roles r ON r.oid=n.nspowner
                        WHERE pg_catalog.has_schema_privilege(current_user,n.oid,'CREATE') ORDER BY n.nspname
                        """),
                    Map.entry("default_acl", """
                        SELECT owner.rolname AS owner,n.nspname,d.defaclobjtype,d.defaclacl FROM pg_catalog.pg_default_acl d
                        JOIN pg_catalog.pg_roles owner ON owner.oid=d.defaclrole LEFT JOIN pg_catalog.pg_namespace n ON n.oid=d.defaclnamespace
                        ORDER BY owner.rolname,n.nspname,d.defaclobjtype
                        """),
                    Map.entry("enabled_event_triggers", """
                        SELECT e.evtname,owner.rolname AS owner,e.evtevent,e.evtenabled,e.evttags,
                        n.nspname AS function_schema,p.proname AS function_name FROM pg_catalog.pg_event_trigger e
                        JOIN pg_catalog.pg_roles owner ON owner.oid=e.evtowner JOIN pg_catalog.pg_proc p ON p.oid=e.evtfoid
                        JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace WHERE e.evtenabled <> 'D' ORDER BY e.evtname
                        """),
                    Map.entry("relation_inventory", """
                        SELECT c.relname,c.relkind,owner.rolname AS owner,c.relacl
                        FROM pg_catalog.pg_class c JOIN pg_catalog.pg_roles owner ON owner.oid=c.relowner
                        WHERE c.relnamespace=pg_catalog.to_regnamespace('dxos_wf25_smoke_01') ORDER BY c.relname
                        """),
                    Map.entry("objects_and_conflicts", """
                        SELECT n.nspname,c.relname,c.relkind,owner.rolname AS owner,c.relacl,
                        pg_catalog.has_table_privilege(current_user,c.oid,'SELECT') AS can_select,
                        pg_catalog.has_table_privilege(current_user,c.oid,'INSERT') AS can_insert,
                        pg_catalog.has_any_column_privilege(current_user,c.oid,'UPDATE') AS can_update,
                        pg_catalog.has_table_privilege(current_user,c.oid,'DELETE') AS can_delete,
                        pg_catalog.has_table_privilege(current_user,c.oid,'TRUNCATE') AS can_truncate,
                        pg_catalog.has_table_privilege(current_user,c.oid,'TRIGGER') AS can_trigger
                        FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                        JOIN pg_catalog.pg_roles owner ON owner.oid=c.relowner WHERE c.relkind IN ('r','p','v','m','f') AND
                        (n.nspname='dxos_wf25_smoke_01' OR c.relname IN ('flyway_schema_history','workflow_definitions',
                        'workflow_definition_states','workflow_definition_transitions','workflow_instances','workflow_transition_history'))
                        ORDER BY n.nspname,c.relname
                        """),
                    Map.entry("update_columns", """
                        SELECT c.relname,a.attname,pg_catalog.has_column_privilege(current_user,c.oid,a.attnum,'UPDATE') AS can_update
                        FROM pg_catalog.pg_attribute a JOIN pg_catalog.pg_class c ON c.oid=a.attrelid
                        WHERE c.relnamespace=pg_catalog.to_regnamespace('dxos_wf25_smoke_01')
                        AND a.attnum>0 AND NOT a.attisdropped AND c.relkind IN ('r','p') ORDER BY c.relname,a.attnum
                        """),
                    Map.entry("table_triggers", """
                        SELECT c.relname,t.tgname,t.tgenabled,t.tgisinternal,n.nspname AS function_schema,p.proname AS function_name
                        FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid=t.tgrelid
                        JOIN pg_catalog.pg_proc p ON p.oid=t.tgfoid JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
                        WHERE c.relnamespace=pg_catalog.to_regnamespace('dxos_wf25_smoke_01') ORDER BY c.relname,t.tgname
                        """),
                    Map.entry("functions", """
                        SELECT p.proname,owner.rolname AS owner,p.prosecdef,p.proacl,
                        pg_catalog.has_function_privilege(current_user,p.oid,'EXECUTE') AS can_execute
                        FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_roles owner ON owner.oid=p.proowner
                        WHERE p.pronamespace=pg_catalog.to_regnamespace('dxos_wf25_smoke_01') ORDER BY p.proname
                        """))) {
                report(connection, output, query.getKey(), query.getValue());
            }
            phase = "privilege_flyway_history";
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery("""
                        SELECT pg_catalog.has_table_privilege(current_user,c.oid,'SELECT') FROM pg_catalog.pg_class c
                        WHERE c.relnamespace=pg_catalog.to_regnamespace('dxos_wf25_smoke_01')
                        AND c.relname='flyway_schema_history' AND c.relkind='r'
                        """)) {
                    if (rows.next()) {
                        if (rows.getBoolean(1)) {
                            report(connection, output, "flyway_history", """
                                    SELECT installed_rank,version,type,checksum,success
                                    FROM "dxos_wf25_smoke_01".flyway_schema_history ORDER BY installed_rank
                                    """);
                        } else {
                            output.println("flyway_history=NOT VERIFIED (SELECT unavailable)");
                        }
                    } else {
                        output.println("flyway_history=ABSENT");
                    }
                }
            }
        }
    }

    private void report(Connection connection, PrintStream output, String label, String sql) throws SQLException {
        phase = (List.of("database_privileges", "roles", "memberships", "schema_create_privileges",
                "objects_and_conflicts", "update_columns", "functions").contains(label) ? "privilege_" : "catalog_") + label;
        output.println("[" + label + "]");
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery(sql)) {
                var metadata = rows.getMetaData();
                while (rows.next()) {
                    for (int column=1; column<=metadata.getColumnCount(); column++) {
                        if (column>1) output.print("; ");
                        output.print(metadata.getColumnLabel(column) + "=" + rows.getString(column));
                    }
                    output.println();
                }
            }
        }
    }
}
