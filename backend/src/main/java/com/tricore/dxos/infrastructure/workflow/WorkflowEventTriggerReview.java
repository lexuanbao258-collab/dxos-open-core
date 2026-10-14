package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Read-only capture for protected local review; never authorizes event triggers. */
public final class WorkflowEventTriggerReview {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final Map<String, List<String>> CONFIRMED = Map.of(
            "pgrst_ddl_watch", List.of("ddl_command_end"),
            "pgrst_drop_watch", List.of("sql_drop"),
            "issue_pg_cron_access", List.of("ddl_command_end", "CREATE EXTENSION"),
            "issue_pg_graphql_access", List.of("ddl_command_end", "CREATE EXTENSION"),
            "issue_pg_net_access", List.of("ddl_command_end", "CREATE EXTENSION"),
            "issue_graphql_placeholder", List.of("sql_drop", "DROP EXTENSION"));

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        System.exit(run(System.getenv(), DriverManager::getConnection, System.out, System.err));
    }

    static int run(Map<String, String> environment, WorkflowReadOnlyPreflight.Connector connector,
                   PrintStream output, PrintStream errors) {
        Path directory;
        List<Long> helpers;
        List<String> signatures;
        try {
            directory = Path.of(Objects.requireNonNull(environment.get("WORKFLOW_EVENT_REVIEW_DIR")));
            verifyDirectory(directory);
            helpers = helperOids(environment.getOrDefault("WORKFLOW_EVENT_REVIEW_HELPER_OIDS", ""));
            String selectors = environment.getOrDefault("WORKFLOW_EVENT_REVIEW_HELPER_SIGNATURES", "");
            signatures = selectors.isBlank() ? List.of() : Arrays.asList(selectors.split("\n", -1));
            if (helpers.size()+signatures.size()>32 || signatures.stream().anyMatch(value -> value.isBlank()
                    || value.length()>1024 || value.indexOf('\r')>=0 || value.indexOf('\0')>=0)) throw new IllegalArgumentException();
        } catch (Exception failure) {
            errors.println("EVENT_REVIEW_FAIL phase=validation error=unsafe_output_or_helpers");
            return 1;
        }
        Snapshot[] captured = new Snapshot[1];
        int exit = WorkflowReadOnlyPreflight.run(environment, connector, output, errors,
                connection -> captured[0] = collect(connection, helpers, signatures));
        if (exit != 0) return exit;
        try {
            // Do not persist source until both rollback and connection close succeeded.
            verifyDirectory(directory);
            Files.writeString(directory.resolve("shareable-metadata.json"),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsString(captured[0].metadata()),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.setOwner(directory.resolve("shareable-metadata.json"), Files.getOwner(directory));
            Files.writeString(directory.resolve("private-function-review.json"),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsString(captured[0].privateReview()),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.setOwner(directory.resolve("private-function-review.json"), Files.getOwner(directory));
            output.println("EVENT_REVIEW_CAPTURE_COMPLETE guard_review=BLOCKED dependency_coverage=CATALOG_ONLY");
            return 0;
        } catch (Exception failure) {
            errors.println("EVENT_REVIEW_FAIL phase=artifact error=protected_export_failed");
            return 1;
        }
    }

    record Snapshot(Map<String, Object> metadata, Map<String, Object> privateReview) {}

    private static Snapshot collect(Connection connection, List<Long> manualHelpers, List<String> signatures) throws Exception {
        if (connection.getAutoCommit()) throw new SQLException("Read-only transaction required", "25001");
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT pg_catalog.current_setting('transaction_read_only')")) {
            if (!rows.next() || !"on".equals(rows.getString(1))) throw new SQLException("Read-only required", "25006");
        }
        return inventory(connection, manualHelpers, signatures, true);
    }

    static Map<String, Object> liveMetadata(Connection connection, List<String> signatures) throws Exception {
        return inventory(connection, List.of(), signatures, false).metadata();
    }

    private static Snapshot inventory(Connection connection, List<Long> manualHelpers, List<String> signatures,
                                      boolean includePrivate) throws Exception {
        Set<Long> resolvedHelpers = new TreeSet<>(manualHelpers);
        for (String signature : signatures) {
            try (var statement=connection.prepareStatement("SELECT pg_catalog.to_regprocedure(?)::oid")) {
                statement.setQueryTimeout(15); statement.setString(1,signature);
                try (var rows=statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Helper missing", "42704");
                    long oid=rows.getLong(1);
                    if (rows.wasNull()) throw new SQLException("Helper missing", "42704");
                    resolvedHelpers.add(oid);
                }
            }
        }
        List<Map<String, Object>> triggers = new ArrayList<>();
        Set<Long> pending = new TreeSet<>(resolvedHelpers);
        boolean matches = true;
        Set<String> names = new HashSet<>();
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(15);
            try (var rows = statement.executeQuery("""
                    SELECT e.evtname,e.evtevent,e.evtenabled,e.evttags,
                           pg_catalog.pg_get_userbyid(e.evtowner) AS owner,e.evtfoid,
                           n.nspname AS function_schema,p.proname
                    FROM pg_catalog.pg_event_trigger e
                    JOIN pg_catalog.pg_proc p ON p.oid=e.evtfoid
                    JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
                    ORDER BY e.evtname
                    """)) {
                while (rows.next()) {
                    String name = rows.getString(1), event = rows.getString(2);
                    java.sql.Array tagArray = rows.getArray(4);
                    boolean unrestricted = tagArray == null;
                    List<String> tags = strings(tagArray);
                    tags.sort(String::compareTo);
                    var item = new TreeMap<String, Object>();
                    item.put("name", name); item.put("event", event); item.put("enabledMode", rows.getString(3));
                    item.put("tags", tags); item.put("tagsUnrestricted", unrestricted);
                    item.put("owner", rows.getString(5)); item.put("functionOid", rows.getLong(6));
                    item.put("functionSchema", rows.getString(7)); item.put("functionName", rows.getString(8));
                    List<String> expected = CONFIRMED.get(name);
                    boolean matching = expected != null && event.equals(expected.getFirst())
                            && tags.equals(expected.subList(1, expected.size()))
                            && unrestricted == (expected.size()==1)
                            && "supabase_admin".equals(rows.getString(5)) && "extensions".equals(rows.getString(7));
                    item.put("matchesConfirmedMetadata", matching);
                    matches &= matching;
                    names.add(name); pending.add(rows.getLong(6)); triggers.add(item);
                }
            }
        }
        matches &= names.equals(CONFIRMED.keySet());
        List<Map<String, Object>> functions = new ArrayList<>(), privateFunctions = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            long oid = pending.iterator().next(); pending.remove(oid);
            if (!visited.add(oid)) continue;
            if (visited.size() > 64) throw new SQLException("Helper review bound exceeded", "54000");
            var function = routine(connection, oid, includePrivate);
            functions.add(function.metadata());
            if (includePrivate) privateFunctions.add(function.privateReview());
            List<Map<String, Object>> dependencies = dependencies(connection, oid);
            function.metadata().put("catalogDependencies", dependencies);
            for (var dependency : dependencies) {
                if (Boolean.TRUE.equals(dependency.get("routineReference"))) {
                    long helper = (Long) dependency.get("oid");
                    try (var statement = connection.prepareStatement("SELECT n.nspname FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace WHERE p.oid=?::oid")) {
                        statement.setQueryTimeout(15);
                        statement.setLong(1, helper);
                        try (var rows = statement.executeQuery()) {
                            if (rows.next() && !"pg_catalog".equals(rows.getString(1))) pending.add(helper);
                        }
                    }
                }
            }
        }
        functions.sort(Comparator.comparingLong(item -> (Long) item.get("oid")));
        var metadata = new TreeMap<String, Object>();
        metadata.put("formatVersion", 1);
        metadata.put("inventoryMatchesConfirmedMetadata", matches);
        metadata.put("guardReviewStatus", "BLOCKED");
        metadata.put("behaviorReview", "NOT_REVIEWED");
        metadata.put("dependencyCoverage", "CATALOG_ONLY_NOT_COMPLETE; manually inspect bodies and dynamic SQL");
        metadata.put("manualHelperOids", List.copyOf(resolvedHelpers));
        metadata.put("session", session(connection));
        metadata.put("triggers", triggers); metadata.put("functions", functions);
        metadata.put("snapshotSha256", sha256(JSON.writeValueAsString(metadata)));
        metadata.put("collectedAtUtc", Instant.now().toString());
        return new Snapshot(metadata, Map.of("warning", "PRIVATE: contains deployed source and configuration; do not share or commit",
                "functions", privateFunctions));
    }

    static Snapshot routine(Connection connection, long oid, boolean includePrivate) throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT p.oid,n.nspname,p.proname,pg_catalog.pg_get_function_identity_arguments(p.oid),
                       r.rolname,r.rolsuper,r.rolbypassrls,l.lanname,p.prosecdef,p.prokind,
                       CASE WHEN ? THEN p.proconfig ELSE ARRAY(SELECT pg_catalog.split_part(value,'=',1)
                           FROM pg_catalog.unnest(p.proconfig) value) END,
                       p.proacl::text,CASE WHEN ? THEN pg_catalog.pg_get_functiondef(p.oid) ELSE NULL END,
                       pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(pg_catalog.pg_get_functiondef(p.oid),'UTF8')),'hex'),
                       r.rolcreatedb,r.rolcreaterole,r.rolreplication,r.rolinherit,
                       pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
                           pg_catalog.array_to_json(COALESCE(p.proconfig,ARRAY[]::text[]))::text,'UTF8')),'hex')
                FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
                JOIN pg_catalog.pg_roles r ON r.oid=p.proowner JOIN pg_catalog.pg_language l ON l.oid=p.prolang
                WHERE p.oid=?::oid AND p.prokind IN ('f','p','w')
                """)) {
            statement.setQueryTimeout(15); statement.setBoolean(1, includePrivate);
            statement.setBoolean(2, includePrivate); statement.setLong(3, oid);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Reviewable routine missing", "42704");
                String definition = rows.getString(13), hash = rows.getString(14);
                if (includePrivate && !hash.equals(sha256(definition))) throw new SQLException("Definition digest mismatch", "22000");
                List<String> config = strings(rows.getArray(11));
                String configHash = rows.getString(19);
                if (includePrivate && !configHash.equals(sha256(JSON.writeValueAsString(config))))
                    throw new SQLException("Configuration digest mismatch", "22000");
                List<String> keys = config.stream().map(value -> value.split("=",2)[0]).sorted().toList();
                var metadata = new TreeMap<String, Object>();
                metadata.put("oid", oid); metadata.put("schema", rows.getString(2)); metadata.put("name", rows.getString(3));
                metadata.put("identityArguments", rows.getString(4)); metadata.put("owner", rows.getString(5));
                metadata.put("ownerSuperuser", rows.getBoolean(6)); metadata.put("ownerBypassRls", rows.getBoolean(7));
                metadata.put("ownerCreateDb", rows.getBoolean(15)); metadata.put("ownerCreateRole", rows.getBoolean(16));
                metadata.put("ownerReplication", rows.getBoolean(17)); metadata.put("ownerInherit", rows.getBoolean(18));
                metadata.put("ownerMemberships", memberships(connection, rows.getString(5)));
                metadata.put("language", rows.getString(8)); metadata.put("securityDefiner", rows.getBoolean(9));
                metadata.put("routineKind", rows.getString(10)); metadata.put("acl", rows.getString(12));
                metadata.put("effectiveAcl", routineAcl(connection,oid));
                metadata.put("definitionSha256", hash); metadata.put("configurationSha256", configHash);
                metadata.put("configurationKeys", keys);
                metadata.put("searchPathConfigured", keys.contains("search_path"));
                return new Snapshot(metadata, includePrivate
                        ? Map.of("oid", oid, "definition", definition, "configuration", config) : Map.of());
            }
        }
    }

    private static List<Map<String, Object>> dependencies(Connection connection, long oid) throws SQLException {
        var result = new ArrayList<Map<String, Object>>();
        try (var statement = connection.prepareStatement("""
                SELECT refclassid::pg_catalog.regclass::text,refobjid,refobjsubid,deptype,
                       refclassid='pg_catalog.pg_proc'::pg_catalog.regclass,
                       (pg_catalog.pg_identify_object(refclassid,refobjid,refobjsubid)).identity
                FROM pg_catalog.pg_depend WHERE classid='pg_catalog.pg_proc'::pg_catalog.regclass AND objid=?::oid
                ORDER BY refclassid,refobjid,refobjsubid,deptype
                """)) {
            statement.setQueryTimeout(15); statement.setLong(1, oid);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) result.add(Map.of("catalog", rows.getString(1), "oid", rows.getLong(2),
                        "subId", rows.getInt(3), "kind", rows.getString(4), "routineReference", rows.getBoolean(5),
                        "identity", rows.getString(6)));
            }
        }
        return result;
    }

    private static Map<String, Object> session(Connection connection) throws SQLException {
        try (var statement=connection.createStatement()) {
            statement.setQueryTimeout(15);
            try (var rows=statement.executeQuery("""
                    SELECT pg_catalog.current_database(),current_user,session_user,
                           pg_catalog.current_setting('server_version'),pg_catalog.current_setting('search_path'),
                           pg_catalog.current_setting('session_replication_role'),pg_catalog.current_setting('transaction_read_only')
                    """)) {
                if (!rows.next()) throw new SQLException("Session evidence unavailable", "02000");
                return Map.of("database",rows.getString(1),"currentUser",rows.getString(2),"sessionUser",rows.getString(3),
                        "version",rows.getString(4),"searchPath",rows.getString(5),
                        "sessionReplicationRole",rows.getString(6),"transactionReadOnly",rows.getString(7));
            }
        }
    }

    private static List<Map<String, Object>> memberships(Connection connection, String owner) throws SQLException {
        var result=new ArrayList<Map<String, Object>>();
        try (var statement=connection.prepareStatement("""
                SELECT granted.rolname,m.admin_option,m.inherit_option,m.set_option
                FROM pg_catalog.pg_auth_members m JOIN pg_catalog.pg_roles granted ON granted.oid=m.roleid
                JOIN pg_catalog.pg_roles member ON member.oid=m.member
                WHERE member.rolname=? ORDER BY granted.rolname,m.grantor
                """)) {
            statement.setQueryTimeout(15); statement.setString(1,owner);
            try (var rows=statement.executeQuery()) {
                while(rows.next()) result.add(Map.of("role",rows.getString(1),"admin",rows.getBoolean(2),
                        "inherit",rows.getBoolean(3),"set",rows.getBoolean(4)));
            }
        }
        return result;
    }

    private static List<Map<String, Object>> routineAcl(Connection connection, long oid) throws SQLException {
        var result=new ArrayList<Map<String, Object>>();
        try (var statement=connection.prepareStatement("""
                SELECT CASE WHEN a.grantee=0 THEN 'PUBLIC' ELSE grantee.rolname END,
                       pg_catalog.pg_get_userbyid(a.grantor),a.privilege_type,a.is_grantable
                FROM pg_catalog.pg_proc p CROSS JOIN LATERAL pg_catalog.aclexplode(
                    COALESCE(p.proacl,pg_catalog.acldefault('f',p.proowner))) a
                LEFT JOIN pg_catalog.pg_roles grantee ON grantee.oid=a.grantee
                WHERE p.oid=?::oid ORDER BY a.grantee,a.grantor,a.privilege_type
                """)) {
            statement.setQueryTimeout(15); statement.setLong(1,oid);
            try (var rows=statement.executeQuery()) {
                while(rows.next()) result.add(Map.of("grantee",rows.getString(1),"grantor",rows.getString(2),
                        "privilege",rows.getString(3),"grantable",rows.getBoolean(4)));
            }
        }
        return result;
    }

    private static List<String> strings(java.sql.Array array) throws SQLException {
        if (array == null) return new ArrayList<>();
        try {
            var result = new ArrayList<String>();
            for (Object value : (Object[]) array.getArray()) result.add((String) value);
            return result;
        } finally { array.free(); }
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Long> helperOids(String value) {
        if (value.isBlank()) return List.of();
        if (!value.matches("[0-9]+(,[0-9]+)*")) throw new IllegalArgumentException();
        List<Long> oids = Arrays.stream(value.split(",")).map(Long::parseLong).distinct().sorted().toList();
        if (oids.size() > 32 || oids.stream().anyMatch(oid -> oid<1 || oid>4294967295L)) throw new IllegalArgumentException();
        return oids;
    }

    static void verifyDirectory(Path directory) throws Exception {
        verifyDirectory(directory, true);
    }

    static void verifyDirectory(Path directory, boolean requireEmpty) throws Exception {
        if (!directory.isAbsolute() || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !directory.toRealPath().equals(directory.normalize())) throw new IllegalArgumentException();
        for (Path parent=directory; parent!=null; parent=parent.getParent())
            if (Files.exists(parent.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException();
        if (requireEmpty) {
            try (var entries=Files.list(directory)) { if (entries.findAny().isPresent()) throw new IllegalArgumentException(); }
        }
        var acl = Files.getFileAttributeView(directory, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            UserPrincipal owner = acl.getOwner();
            if (acl.getAcl().isEmpty()) throw new IllegalArgumentException();
            boolean inheritableOwner = false;
            for (AclEntry entry : acl.getAcl())
                if (entry.type()==AclEntryType.ALLOW && !entry.principal().equals(owner)
                        && !entry.principal().getName().equalsIgnoreCase("NT AUTHORITY\\SYSTEM")) throw new IllegalArgumentException();
                else if (entry.type()==AclEntryType.ALLOW && entry.principal().equals(owner)
                        && entry.flags().contains(AclEntryFlag.FILE_INHERIT)
                        && entry.permissions().containsAll(Set.of(AclEntryPermission.READ_DATA,AclEntryPermission.WRITE_DATA)))
                    inheritableOwner = true;
            if (!inheritableOwner) throw new IllegalArgumentException();
        } else {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS);
            if (!permissions.equals(EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE)))
                throw new IllegalArgumentException();
        }
    }
}
