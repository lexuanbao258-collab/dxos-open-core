package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;

/** Captures evidence only; neither metadata nor private capture grants approval. */
public final class WorkflowSecurityDefinerReview {
    static final String SQL_SHA256 = "bc70781e66f90b8d4b5b405f5f77c5fb2fb70b38b2b7f7c4aeda8e29dc8dcca1";
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final List<String> SECTIONS = List.of("session", "roles", "public_database", "routines",
            "schema_acl", "owner_memberships", "dependencies");
    private static final Set<String> COLUMNS = Set.of("server_version", "transaction_read_only", "search_path", "database_name",
            "session_user", "current_user", "backend_ssl", "name", "status", "rolcanlogin", "rolsuper", "rolcreaterole",
            "rolcreatedb", "rolbypassrls", "privilege_type", "is_grantable", "oid", "schema_name", "routine_name",
            "identity_arguments", "signature", "owner", "owner_superuser", "owner_bypass_rls", "owner_create_role",
            "owner_create_db", "owner_replication", "language", "extension_name", "extension_version", "security_definer",
            "routine_kind", "return_type", "call_context", "execute_acl", "function_search_path", "definition_sha256",
            "configuration_sha256", "public_execute", "public_schema_usage", "runtime_exists", "runtime_execute",
            "runtime_schema_usage", "runtime_inherits_owner", "runtime_can_set_owner", "service_role_execute",
            "service_role_schema_usage", "grantee", "grantor", "origin_role", "reached_role", "membership_path",
            "inherit_path", "set_path", "effective_inherit", "effective_set", "effective_admin", "direction", "routine_oid",
            "related_catalog", "related_oid", "related_sub_id", "dependency_type", "related_identity");

    private WorkflowSecurityDefinerReview() {}

    static List<String> queries(Path file) throws Exception {
        var queries = WorkflowRuntimePrivilegeAudit.queries(file, SQL_SHA256);
        if (queries.size() != SECTIONS.size() + 1) throw new IllegalArgumentException("Capture sections rejected");
        return queries;
    }

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        if (args.length != 1) {
            System.err.println("SECURITY_DEFINER_CAPTURE=FAIL phase=validation");
            System.exit(1);
        }
        System.exit(run(System.getenv(), Path.of(args[0]), DriverManager::getConnection, System.out, System.err));
    }

    static int run(Map<String, String> environment, Path sql, WorkflowReadOnlyPreflight.Connector connector,
                   PrintStream output, PrintStream errors) {
        String phase = "validation";
        int exit = 0;
        Connection connection = null;
        boolean started = false;
        Path directory = null;
        Map<String, Object> catalog = new TreeMap<>();
        List<Map<String, Object>> privateReview = new ArrayList<>();
        boolean capturePrivate = false;
        try {
            var queries = queries(sql);
            directory = Path.of(Objects.requireNonNull(environment.get("WORKFLOW_DEFINER_REVIEW_DIR")));
            WorkflowEventTriggerReview.verifyDirectory(directory);
            String baselinePath = environment.get("WORKFLOW_DEFINER_METADATA_FILE");
            Map<?, ?> baseline = null;
            Set<Long> helperOids = new TreeSet<>();
            if (baselinePath != null) {
                baseline = baseline(Path.of(baselinePath));
                capturePrivate = true;
                var expectedCatalog = (Map<?, ?>) baseline.get("catalog");
                for (Object value : (List<?>) expectedCatalog.get("manual_helper_oids")) {
                    long oid = ((Number) value).longValue();
                    if (oid < 1 || oid > 4294967295L || !helperOids.add(oid)) throw new IllegalArgumentException();
                }
            }
            String selectors = environment.getOrDefault("WORKFLOW_DEFINER_HELPER_SIGNATURES", "");
            List<String> signatures = selectors.isBlank() ? List.of() : Arrays.asList(selectors.split("\n", -1));
            if (helperOids.size() + signatures.size() > 32 || signatures.stream().anyMatch(value ->
                    value.isBlank() || value.length() > 1024 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0))
                throw new IllegalArgumentException();
            var properties = WorkflowLiveGuard.connectionProperties(environment);
            properties.setProperty("ApplicationName", "dxos-wf25-security-definer-review");
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
            phase = "session";
            var sessionRows = rows(connection, queries.getFirst(), null);
            if (sessionRows.size() != 1) throw new IllegalArgumentException();
            var session = sessionRows.getFirst();
            String expectedRole = environment.get("DB_USER").split("\\.", 2)[0];
            if (!"on".equals(session.get("transaction_read_only"))
                    || !"pg_catalog,pg_temp".equals(((String) session.get("search_path")).replace(" ", ""))
                    || !((String) session.get("server_version")).startsWith("17.")
                    || !expectedRole.equals(session.get("session_user")) || !expectedRole.equals(session.get("current_user"))
                    || !environment.get("DB_NAME").equals(session.get("database_name"))
                    || !environment.get("DB_NAME").equals(connection.getCatalog())
                    || !(session.get("backend_ssl") instanceof Boolean)) throw new IllegalArgumentException();
            catalog.put("session", sessionRows);
            phase = "helper_selection";
            for (String signature : signatures) {
                try (var statement = connection.prepareStatement(queries.getLast())) {
                    statement.setQueryTimeout(15);
                    statement.setString(1, signature);
                    try (var result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalArgumentException();
                        long oid = result.getLong(1);
                        if (result.wasNull() || oid < 1 || oid > 4294967295L || result.next()) throw new IllegalArgumentException();
                        helperOids.add(oid);
                    }
                }
            }
            catalog.put("manual_helper_oids", new ArrayList<>(helperOids));
            for (int index = 1; index < SECTIONS.size(); index++) {
                phase = SECTIONS.get(index);
                catalog.put(phase, rows(connection, queries.get(index), index >= 3 ? helperOids : null));
            }
            phase = "evidence";
            validateEvidence(catalog);
            String fingerprint = sha256(JSON.writeValueAsString(catalog));
            if (baseline != null) {
                phase = "baseline";
                if (!fingerprint.equals(baseline.get("snapshot_sha256"))) throw new IllegalArgumentException("Catalog drift");
                phase = "private_review";
                for (var routine : routineRows(catalog)) {
                    var captured = WorkflowEventTriggerReview.routine(connection, ((Number) routine.get("oid")).longValue(), true);
                    if (!routine.get("definition_sha256").equals(captured.metadata().get("definitionSha256"))
                            || !routine.get("configuration_sha256").equals(captured.metadata().get("configurationSha256")))
                        throw new IllegalArgumentException("Private digest mismatch");
                    privateReview.add(captured.privateReview());
                }
            }
        } catch (Exception | LinkageError failure) {
            errors.println("SECURITY_DEFINER_CAPTURE=FAIL phase=" + phase);
            exit = 1;
        } finally {
            if (connection != null) {
                if (started) {
                    try { connection.rollback(); }
                    catch (Exception failure) { errors.println("SECURITY_DEFINER_CAPTURE=FAIL phase=rollback"); exit = 1; }
                }
                try { connection.close(); }
                catch (Exception failure) { errors.println("SECURITY_DEFINER_CAPTURE=FAIL phase=close"); exit = 1; }
            }
        }
        if (exit != 0) return 1;
        try {
            WorkflowEventTriggerReview.verifyDirectory(directory);
            if (capturePrivate) write(directory, "private-function-review.json", Map.of("functions", privateReview));
            var metadata = new TreeMap<String, Object>();
            metadata.put("format_version", 1);
            metadata.put("collected_at_utc", Instant.now().toString());
            metadata.put("sql_sha256", SQL_SHA256);
            metadata.put("catalog", catalog);
            metadata.put("snapshot_sha256", sha256(JSON.writeValueAsString(catalog)));
            metadata.put("dependency_coverage", "CATALOG_ONLY_NOT_COMPLETE; inspect bodies, dynamic SQL and helper chains locally");
            metadata.put("private_capture", capturePrivate ? "COMPLETE_BEHAVIOR_NOT_REVIEWED" : "NOT_REQUESTED");
            metadata.put("routine_reviews", routineRows(catalog).stream().map(WorkflowSecurityDefinerReview::review).toList());
            write(directory, "shareable-metadata.json", metadata);
            output.println("SECURITY_DEFINER_CAPTURE=PASS scope=READ_ONLY_CATALOG client_tls=VERIFIED_VERIFY_FULL");
            output.println("BEHAVIOR_REVIEW=NEEDS_REVIEW; WRITE_READINESS=BLOCKED");
            return 0;
        } catch (Exception failure) {
            errors.println("SECURITY_DEFINER_CAPTURE=FAIL phase=artifact");
            return 1;
        }
    }

    private static List<Map<String, Object>> rows(Connection connection, String query, Set<Long> helperOids) throws Exception {
        var data = new ArrayList<Map<String, Object>>();
        try (var statement = connection.prepareStatement(query)) {
            statement.setQueryTimeout(15);
            java.sql.Array helpers = helperOids == null ? null : connection.createArrayOf("oid", helperOids.toArray());
            try {
                if (helpers != null) statement.setArray(1, helpers);
                try (var result = statement.executeQuery()) {
                    var columns = result.getMetaData();
                    while (result.next()) {
                        if (data.size() >= 10000) throw new IllegalArgumentException("Evidence limit exceeded");
                        var row = new TreeMap<String, Object>();
                        for (int column = 1; column <= columns.getColumnCount(); column++) {
                            String label = columns.getColumnLabel(column);
                            if (!COLUMNS.contains(label)) throw new IllegalArgumentException("Unexpected catalog column");
                            Object value = result.getObject(column);
                            if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean))
                                throw new IllegalArgumentException("Unexpected catalog type");
                            row.put(label, value);
                        }
                        data.add(row);
                    }
                }
            } finally { if (helpers != null) helpers.free(); }
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> routineRows(Map<String, Object> catalog) {
        return (List<Map<String, Object>>) catalog.get("routines");
    }

    @SuppressWarnings("unchecked")
    private static void validateEvidence(Map<String, Object> catalog) {
        var roles = (List<Map<String, Object>>) catalog.get("roles");
        if (roles.size() != 2 || !roles.stream().map(row -> row.get("name")).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("dxos_wf25_runtime", "dxos_wf25_migrator"))) throw new IllegalArgumentException();
        if (roles.stream().anyMatch(row -> !Set.of("PRESENT", "NOT_PROVISIONED").contains(row.get("status"))))
            throw new IllegalArgumentException();
        boolean runtimeExists = roles.stream().anyMatch(row -> row.get("name").equals("dxos_wf25_runtime")
                && row.get("status").equals("PRESENT"));
        Set<Long> oids = new HashSet<>();
        if (routineRows(catalog).size() > 256) throw new IllegalArgumentException();
        for (var routine : routineRows(catalog)) {
            long oid = ((Number) routine.get("oid")).longValue();
            if (oid < 1 || oid > 4294967295L || !oids.add(oid) || !Boolean.valueOf(runtimeExists).equals(routine.get("runtime_exists"))
                    || !((String) routine.get("definition_sha256")).matches("[0-9a-f]{64}")
                    || !((String) routine.get("configuration_sha256")).matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
            for (String label : List.of("runtime_execute", "runtime_schema_usage", "runtime_inherits_owner", "runtime_can_set_owner")) {
                if (runtimeExists ? !(routine.get(label) instanceof Boolean) : routine.get(label) != null)
                    throw new IllegalArgumentException("Unknown privileges must remain null");
            }
        }
        if (!oids.containsAll((List<Long>) catalog.get("manual_helper_oids"))) throw new IllegalArgumentException("Missing helper evidence");
    }

    private static Map<String, Object> review(Map<String, Object> routine) {
        var result = new TreeMap<String, Object>();
        result.put("oid", routine.get("oid"));
        result.put("signature", routine.get("signature"));
        result.put("status", "NEEDS_REVIEW");
        result.put("direct_runtime", Boolean.TRUE.equals(routine.get("runtime_exists"))
                ? "ACL_RECORDED_BEHAVIOR_NOT_REVIEWED" : "UNKNOWN_NOT_PROVISIONED");
        result.put("potential_public_direct", Boolean.TRUE.equals(routine.get("public_execute"))
                && Boolean.TRUE.equals(routine.get("public_schema_usage")) && "ORDINARY_CALL".equals(routine.get("call_context")));
        result.put("indirect_runtime", "UNKNOWN_CATALOG_DEPENDENCIES_ARE_INCOMPLETE");
        return result;
    }

    private static Map<?, ?> baseline(Path file) throws Exception {
        if (!file.isAbsolute() || !"shareable-metadata.json".equals(file.getFileName().toString())
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().equals(file.normalize())
                || Files.size(file) > 8 * 1024 * 1024) throw new IllegalArgumentException();
        WorkflowEventTriggerReview.verifyDirectory(file.getParent(), false);
        var owner = Files.getOwner(file.getParent());
        if (!owner.equals(Files.getOwner(file))) throw new IllegalArgumentException();
        var acl = Files.getFileAttributeView(file, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            if (acl.getAcl().isEmpty() || acl.getAcl().stream().anyMatch(entry -> entry.type() == AclEntryType.ALLOW
                    && !entry.principal().equals(owner) && !entry.principal().getName().equalsIgnoreCase("NT AUTHORITY\\SYSTEM")))
                throw new IllegalArgumentException();
        } else if (!Files.getPosixFilePermissions(file).equals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)))
            throw new IllegalArgumentException();
        Map<?, ?> metadata = JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), Map.class);
        if (!Integer.valueOf(1).equals(metadata.get("format_version")) || !SQL_SHA256.equals(metadata.get("sql_sha256"))
                || !sha256(JSON.writeValueAsString(metadata.get("catalog"))).equals(metadata.get("snapshot_sha256")))
            throw new IllegalArgumentException("Invalid metadata fingerprint");
        return metadata;
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void write(Path directory, String name, Object value) throws Exception {
        Path file = directory.resolve(name);
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.setOwner(file, Files.getOwner(directory));
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null)
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }
}
