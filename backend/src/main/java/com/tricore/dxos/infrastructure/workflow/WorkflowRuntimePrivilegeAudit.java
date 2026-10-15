package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Standalone catalog audit: no Spring, Flyway, provisioning or workflow writer. */
public final class WorkflowRuntimePrivilegeAudit {
    static final String SQL_SHA256 = "4cc070ba08d481b915c7d52ce04a5e26478c3a878c5dc181f517bf04352185a9";
    private static final List<String> SECTIONS = List.of("session", "roles", "database", "public_database",
            "schemas", "membership_paths", "memberships", "tables", "columns", "outside_tables",
            "sequences", "runtime_ownership", "routines", "default_privileges", "flyway_metadata");
    private static final Set<String> ROLES = Set.of("postgres", "dxos_wf25_migrator", "dxos_wf25_runtime");
    private static final Set<String> PRIVILEGES = Set.of("CONNECT", "CREATE", "TEMP", "TEMPORARY", "USAGE",
            "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "TRIGGER", "REFERENCES", "MAINTAIN", "EXECUTE");

    private WorkflowRuntimePrivilegeAudit() {}

    static List<String> queries(Path file) throws Exception {
        List<String> queries = queries(file, SQL_SHA256);
        if (queries.size() != SECTIONS.size()) throw new IllegalArgumentException("Audit sections rejected");
        return queries;
    }

    static List<String> queries(Path file, String expectedSha256) throws Exception {
        String sql = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(sql.getBytes(StandardCharsets.UTF_8)));
        if (!expectedSha256.equals(hash)) throw new IllegalArgumentException("Unreviewed audit SQL");
        List<String> statements = java.util.Arrays.stream(sql.replaceAll("(?m)^--[^\r\n]*", "").split(";"))
                .map(String::trim)
                .filter(text -> !text.isEmpty()).toList();
        if (statements.size() < 3
                || !statements.getFirst().equals("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY")
                || !statements.getLast().equals("ROLLBACK")) throw new IllegalArgumentException("Audit boundaries rejected");
        var queries = statements.subList(1, statements.size() - 1);
        if (queries.stream().anyMatch(query -> !query.startsWith("SELECT ") && !query.startsWith("WITH RECURSIVE ")))
            throw new IllegalArgumentException("Audit statement rejected");
        return queries;
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        if (args.length != 1) {
            System.err.println("RUNTIME_PRIVILEGE_AUDIT=FAIL phase=validation");
            System.exit(1);
        }
        System.exit(run(System.getenv(), Path.of(args[0]), java.sql.DriverManager::getConnection, System.out, System.err));
    }

    static int run(Map<String, String> environment, Path file, WorkflowReadOnlyPreflight.Connector connector,
                   PrintStream output, PrintStream errors) {
        String phase = "validation";
        Connection connection = null;
        boolean started = false;
        int exit = 0;
        var report = new LinkedHashMap<String, Object>();
        var roles = new LinkedHashMap<String, String>();
        var summaries = new ArrayList<Map<String, Object>>();
        boolean publicTemp = false, publicCreate = false;
        try {
            List<String> queries = queries(file);
            var properties = WorkflowLiveGuard.connectionProperties(environment);
            properties.setProperty("ApplicationName", "dxos-wf25-runtime-privilege-audit");
            // Catalog-only work must not resolve operators/functions through the application schema.
            properties.setProperty("currentSchema", "pg_catalog,pg_temp");
            String url = "jdbc:postgresql://" + environment.get("DB_HOST") + ":" + environment.get("DB_PORT")
                    + "/" + environment.get("DB_NAME");
            phase = "connect_tls";
            connection = connector.connect(url, properties);
            phase = "transaction";
            if (!connection.getAutoCommit()) throw new IllegalArgumentException("Ambient transaction rejected");
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            started = true;
            phase = "read_only";
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery("SELECT pg_catalog.current_setting('transaction_read_only'), "
                        + "pg_catalog.current_setting('search_path'), session_user, current_user")) {
                    String expectedRole = environment.get("DB_USER").split("\\.", 2)[0];
                    if (!rows.next() || !"on".equals(rows.getString(1))
                            || !"pg_catalog,pg_temp".equals(rows.getString(2).replace(" ", ""))
                            || !expectedRole.equals(rows.getString(3)) || !expectedRole.equals(rows.getString(4))
                            || !environment.get("DB_NAME").equals(connection.getCatalog()))
                        throw new IllegalArgumentException("Read-only session rejected");
                }
            }
            report.put("session_identity_verified", true);
            for (int index = 0; index < queries.size(); index++) {
                String section = SECTIONS.get(index);
                phase = section;
                int count = 0;
                var trueCounts = new LinkedHashMap<String, Integer>();
                var privilegeCounts = new LinkedHashMap<String, Integer>();
                var groupedTrueCounts = new LinkedHashMap<String, Map<String, Integer>>();
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(15);
                    try (var rows = statement.executeQuery(queries.get(index))) {
                        var metadata = rows.getMetaData();
                        while (rows.next()) {
                            count++;
                            if (section.equals("session")) {
                                if (!rows.getString("server_version").startsWith("17."))
                                    throw new IllegalArgumentException("PostgreSQL 17 required");
                                Object backendSsl = rows.getObject("backend_ssl");
                                if (backendSsl != null && !(backendSsl instanceof Boolean))
                                    throw new IllegalArgumentException("TLS evidence rejected");
                                report.put("pooler_backend_ssl", backendSsl);
                                report.put("schema_present", rows.getBoolean("schema_present"));
                                report.put("history_present", rows.getBoolean("history_present"));
                                String selfGrant = rows.getString("createrole_self_grant");
                                report.put("creator_self_grant_review_required", selfGrant != null && !selfGrant.isBlank());
                                report.put("session_replication_origin", "origin".equals(rows.getString("session_replication_role")));
                                report.put("event_triggers_enabled", "on".equals(rows.getString("event_triggers")));
                            }
                            if (section.equals("roles")) {
                                String role = rows.getString("name"), status = rows.getString("status");
                                if (!ROLES.contains(role) || !Set.of("PRESENT", "NOT_PROVISIONED").contains(status)
                                        || roles.putIfAbsent(role, status) != null)
                                    throw new IllegalArgumentException("Role evidence rejected");
                            }
                            String subject = "all", rowPrivilege = "all";
                            var rowTrueLabels = new ArrayList<String>();
                            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                                String label = metadata.getColumnLabel(column);
                                Object value = rows.getObject(column);
                                if (Boolean.TRUE.equals(value)) {
                                    trueCounts.merge(label, 1, Integer::sum);
                                    rowTrueLabels.add(label);
                                }
                                if (Set.of("rolname", "name", "member", "creator").contains(label) && value instanceof String role)
                                    subject = ROLES.contains(role) || Set.of("PUBLIC", "anon", "authenticated", "service_role").contains(role)
                                            ? role : "OTHER";
                                if ((label.equals("privilege") || label.equals("privilege_type"))
                                        && value instanceof String privilege && PRIVILEGES.contains(privilege)) {
                                    privilegeCounts.merge(privilege, 1, Integer::sum);
                                    rowPrivilege = privilege;
                                    if (section.equals("public_database")) {
                                        publicTemp |= privilege.equals("TEMP") || privilege.equals("TEMPORARY");
                                        publicCreate |= privilege.equals("CREATE");
                                    }
                                }
                            }
                            var group = groupedTrueCounts.computeIfAbsent(subject + ":" + rowPrivilege, key -> new LinkedHashMap<>());
                            group.merge("rows", 1, Integer::sum);
                            rowTrueLabels.forEach(label -> group.merge(label, 1, Integer::sum));
                        }
                    }
                }
                if ((section.equals("session") && count != 1) || (section.equals("roles") && !roles.keySet().equals(ROLES)))
                    throw new IllegalArgumentException("Incomplete catalog evidence");
                summaries.add(Map.of("section", section, "rows", count, "true_counts", trueCounts,
                        "privilege_rows", privilegeCounts, "by_role_privilege", groupedTrueCounts));
                if (section.equals("schemas")) report.put("public_schema_create_count",
                        groupedTrueCounts.getOrDefault("PUBLIC:all", Map.of()).getOrDefault("create", 0));
            }
            report.put("roles", roles);
            report.put("public_temp", publicTemp);
            report.put("public_database_create", publicCreate);
            report.put("sections", summaries);
        } catch (Exception | LinkageError failure) {
            errors.println("RUNTIME_PRIVILEGE_AUDIT=FAIL phase=" + phase);
            exit = 1;
        } finally {
            if (connection != null) {
                if (started) {
                    try { connection.rollback(); } catch (Exception failure) {
                        errors.println("RUNTIME_PRIVILEGE_AUDIT=FAIL phase=rollback");
                        exit = 1;
                    }
                }
                try { connection.close(); } catch (Exception failure) {
                    errors.println("RUNTIME_PRIVILEGE_AUDIT=FAIL phase=close");
                    exit = 1;
                }
            }
        }
        if (exit == 0) {
            try {
                // Only fixed role/status/privilege labels, booleans and counts; no catalog identifiers or source.
                String safeReport = new ObjectMapper().writeValueAsString(report);
                output.println("RUNTIME_PRIVILEGE_AUDIT=PASS scope=READ_ONLY_CATALOG; transaction_read_only=on; client_tls=VERIFIED_VERIFY_FULL");
                output.println("AUDIT_REPORT=" + safeReport);
                boolean provisioned = "PRESENT".equals(roles.get("dxos_wf25_migrator"))
                        && "PRESENT".equals(roles.get("dxos_wf25_runtime"));
                output.println("WRITE_READINESS=BLOCKED; roles=" + (provisioned ? "PRESENT_REVIEW_REQUIRED" : "NOT_PROVISIONED"));
            } catch (Exception failure) {
                errors.println("RUNTIME_PRIVILEGE_AUDIT=FAIL phase=report");
                return 1;
            }
        }
        return exit;
    }
}
