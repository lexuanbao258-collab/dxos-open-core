package com.tricore.dxos.infrastructure.workflow;

import com.tricore.dxos.core.workflow.*;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Explicit check/migrate/smoke/read commands; never boot Spring or provision/delete cloud objects. */
public final class WorkflowSmokeRunner {
    public static final String SCHEMA = "dxos_wf25_smoke_01";
    private static final List<String> TABLES = List.of("workflow_definitions", "workflow_definition_states",
            "workflow_definition_transitions", "workflow_instances", "workflow_transition_history");
    private static final Instant TIME = Instant.parse("2026-10-13T01:02:03.123456789Z");
    private final WorkflowSchema schema = new WorkflowSchema(SCHEMA);
    private final DataSource migrator;
    private final DataSource runtime;
    private final Path bundle;
    private final WorkflowDevelopmentSmokeRunner.Authorization development;

    public WorkflowSmokeRunner(DataSource migrator, DataSource runtime, Path bundle) {
        this(migrator, runtime, bundle, null);
    }

    private WorkflowSmokeRunner(DataSource migrator, DataSource runtime, Path bundle,
                                WorkflowDevelopmentSmokeRunner.Authorization development) {
        this.development = development;
        this.bundle = bundle.toAbsolutePath().normalize();
        verifyBundle();
        java.util.function.Consumer<Connection> check = connection -> {
            verifyBundle();
            if (development != null) development.verify(connection);
        };
        this.migrator = schema.guard(migrator, check);
        this.runtime = schema.guard(runtime, check);
    }

    static WorkflowSmokeRunner forDevelopment(DataSource operator, Path bundle,
                                             WorkflowDevelopmentSmokeRunner.Authorization authorization) {
        return new WorkflowSmokeRunner(operator, operator, bundle, Objects.requireNonNull(authorization));
    }

    private void verifyBundle() {
        WorkflowEventTriggerGuard.verifyBundle(bundle);
    }

    private org.flywaydb.core.Flyway flyway() {
        verifyBundle();
        return WorkflowMigrations.configure(migrator, SCHEMA, "filesystem:" + bundle)
                .target("4").loggers("slf4j").load();
    }

    /** Read-only preflight. EMPTY requires a pre-created empty schema owned by the migrator. */
    public String check() throws SQLException {
        return inspect(true);
    }

    private String inspect(boolean requireRuntimeWrites) throws SQLException {
        String ownerUser;
        try (Connection c = readConnection(migrator)) {
            try (var s = c.prepareStatement("""
                    SELECT current_user,
                        n.nspowner = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = current_user),
                        pg_catalog.has_schema_privilege(current_user, n.oid, 'CREATE'),
                        pg_catalog.has_language_privilege(current_user, 'plpgsql', 'USAGE'),
                        pg_catalog.current_setting('default_transaction_read_only')
                    FROM pg_catalog.pg_namespace n WHERE n.nspname = ?
                    """)) {
                s.setString(1, SCHEMA);
                try (var r = s.executeQuery()) {
                    require(r.next() && r.getBoolean(2) && r.getBoolean(3) && r.getBoolean(4)
                            && "off".equals(r.getString(5)), "Migrator permissions or transaction mode rejected");
                    ownerUser = r.getString(1);
                }
            }
            try (var s = c.createStatement(); var r = s.executeQuery("""
                    SELECT pg_catalog.current_setting('fsync') = 'on',
                        pg_catalog.current_setting('full_page_writes') = 'on',
                        (SELECT count(*) FROM pg_catalog.pg_default_acl
                         WHERE defaclrole = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = current_user)
                         AND (defaclnamespace = 0 OR defaclnamespace = pg_catalog.to_regnamespace('dxos_wf25_smoke_01'))) = 0
                    """)) {
                r.next();
                require(r.getBoolean(1) && r.getBoolean(2), "Database durability settings rejected");
                require(r.getBoolean(3), "Custom migrator default privileges require separate review");
            }
            WorkflowEventTriggerGuard.REVIEWED.verify(c, SCHEMA);
            c.rollback();
        }
        try (Connection c = readConnection(runtime);
             var s = c.prepareStatement("""
                     SELECT current_user, r.rolsuper, r.rolcreaterole, r.rolcreatedb, r.rolbypassrls,
                            pg_catalog.has_schema_privilege(current_user, ?, 'CREATE'),
                            pg_catalog.pg_has_role(current_user, ?, 'MEMBER'),
                            pg_catalog.current_setting('default_transaction_read_only')
                     FROM pg_catalog.pg_roles r WHERE r.rolname = current_user
                     """)) {
            s.setString(1, SCHEMA);
            s.setString(2, ownerUser);
            try (var r = s.executeQuery()) {
                if (development == null) {
                    require(r.next() && !ownerUser.equals(r.getString(1))
                            && !r.getBoolean(2) && !r.getBoolean(3) && !r.getBoolean(4)
                            && !r.getBoolean(5) && !r.getBoolean(6) && !r.getBoolean(7)
                            && "off".equals(r.getString(8)), "Runtime role or transaction mode rejected");
                } else {
                    require(r.next() && "postgres".equals(ownerUser) && ownerUser.equals(r.getString(1))
                            && "off".equals(r.getString(8)), "Development operator rejected");
                }
            }
            c.rollback();
        }
        boolean empty;
        boolean ready;
        if (development != null) {
            // Development smoke reads the pinned history directly; it never invokes Flyway.
            try (Connection c = readConnection(migrator)) {
                var history = WorkflowApprovedMigration.history(c);
                empty = "ABSENT".equals(history.state());
                ready = history.ready();
                c.rollback();
            }
        } else {
            MigrationInfo[] migrations = flyway().info().all();
            require(migrations.length == 2, "Unexpected migration history");
            empty = Arrays.stream(migrations).allMatch(i -> i.getState() == MigrationState.PENDING);
            ready = Arrays.stream(migrations).allMatch(i -> i.getState() == MigrationState.SUCCESS
                    && Objects.equals(i.getAppliedChecksum(), i.getResolvedChecksum()));
        }
        require(empty || ready, "Migration state or checksum mismatch");
        try (Connection c = readConnection(migrator)) {
            Set<String> tables = new HashSet<>();
            try (var s = c.prepareStatement("""
                    SELECT relname FROM pg_catalog.pg_class
                    WHERE relnamespace = pg_catalog.to_regnamespace(?) AND relkind IN ('r','p','v','m','f','S')
                    """)) {
                s.setString(1, SCHEMA);
                try (var r = s.executeQuery()) {
                    while (r.next()) tables.add(r.getString(1));
                }
            }
            if (empty) {
                require(tables.isEmpty(), "Existing objects in unmigrated schema");
                try (var s = c.prepareStatement("SELECT count(*) FROM pg_catalog.pg_proc WHERE pronamespace = pg_catalog.to_regnamespace(?)")) {
                    s.setString(1, SCHEMA);
                    try (var r = s.executeQuery()) {
                        r.next();
                        require(r.getInt(1) == 0, "Existing functions in unmigrated schema");
                    }
                }
            } else {
                Set<String> expected = new HashSet<>(TABLES);
                expected.add("flyway_schema_history");
                require(tables.equals(expected), "Unexpected schema objects");
                for (String table : TABLES) {
                    try (var s = c.prepareStatement("""
                            SELECT count(*) FROM pg_catalog.pg_roles
                            WHERE rolname IN ('anon','authenticated','service_role')
                              AND (pg_catalog.has_table_privilege(oid, ?, 'SELECT')
                                OR pg_catalog.has_table_privilege(oid, ?, 'INSERT')
                                OR pg_catalog.has_table_privilege(oid, ?, 'UPDATE')
                                OR pg_catalog.has_table_privilege(oid, ?, 'DELETE'))
                            """)) {
                        String object = schema.quoted() + "." + table;
                        for (int i = 1; i <= 4; i++) s.setString(i, object);
                        try (var r = s.executeQuery()) {
                            r.next();
                            require(r.getInt(1) == 0, "Unexpected API table permissions");
                        }
                    }
                }
            }
            c.rollback();
        }
        if (ready && requireRuntimeWrites && development == null) {
            try (Connection c = readConnection(runtime)) {
                for (String table : TABLES) {
                    String object = schema.quoted() + "." + table;
                    try (var s = c.prepareStatement("""
                            SELECT pg_catalog.has_table_privilege(current_user, ?, 'SELECT'),
                                   pg_catalog.has_table_privilege(current_user, ?, 'INSERT'),
                        pg_catalog.has_any_column_privilege(current_user, ?, 'UPDATE'),
                                   pg_catalog.has_table_privilege(current_user, ?, 'DELETE'),
                        pg_catalog.has_table_privilege(current_user, ?, 'TRUNCATE'),
                        pg_catalog.has_table_privilege(current_user, ?, 'TRIGGER')
                            """)) {
                    for (int i = 1; i <= 6; i++) s.setString(i, object);
                        try (var r = s.executeQuery()) {
                            r.next();
                            boolean writable = table.equals("workflow_instances") || table.equals("workflow_transition_history");
                            require(r.getBoolean(1) && r.getBoolean(2) == writable
                                    && r.getBoolean(3) == table.equals("workflow_instances")
                                && !r.getBoolean(4) && !r.getBoolean(5) && !r.getBoolean(6), "Runtime table permissions rejected");
                        }
                    }
                if (table.equals("workflow_instances")) {
                    try (var s = c.prepareStatement("""
                            SELECT count(*) FROM pg_catalog.pg_attribute
                            WHERE attrelid = pg_catalog.to_regclass(?) AND attnum > 0 AND NOT attisdropped
                            AND pg_catalog.has_column_privilege(current_user, attrelid, attnum, 'UPDATE')
                                <> (attname IN ('current_state','lifecycle','runtime_version','updated_at_seconds','updated_at_nanos'))
                            """)) {
                        s.setString(1, object);
                        try (var r = s.executeQuery()) {
                            r.next(); require(r.getInt(1) == 0, "Runtime update columns rejected");
                        }
                    }
                }
                }
                c.rollback();
            }
        }
        return empty ? "EMPTY" : "READY";
    }

    public void migrate() throws SQLException {
        if (development != null) development.requireWrites();
        require("EMPTY".equals(inspect(false)), "Schema is already migrated");
        verifyBundle();
        flyway().migrate();
        require("READY".equals(inspect(false)), "Migration not confirmed");
    }

    public void smoke(String runId) throws Exception {
        if (development != null) development.requireWrites();
        validateRunId(runId);
        require("READY".equals(check()), "Schema is not ready");
        var persistence = new PostgresWorkflowPersistence(runtime, SCHEMA);
        var workflow = new WorkflowRuntime(persistence, Clock.fixed(TIME, ZoneOffset.UTC));
        String review = id(runId, "review");
        String incident = id(runId, "incident");
        for (String id : List.of(review, incident)) {
            try {
                persistence.loadInstance(id);
                throw new IllegalArgumentException("Run identifier already exists");
            } catch (WorkflowException failure) {
                if (failure.code() != WorkflowErrorCode.INSTANCE_NOT_FOUND) throw failure;
            }
        }
        WorkflowInstance initial = workflow.createInstance(review, "document-review", 1,
                new ResourceReference("issue25-smoke", review));
        workflow.createInstance(incident, "incident-resolution", 1, new ResourceReference("issue25-smoke", incident));
        WorkflowDefinition definition = persistence.loadDefinition("document-review", 1);
        WorkflowInstance active = initial.activate(definition, TIME);
        race(() -> persistence.commitActivation(initial, active), () -> persistence.commitActivation(initial, active));
        var proposal = active.transition(definition, new TransitionCommand(review, "submit", actor(runId), 0), TIME);
        race(() -> persistence.commitTransition(proposal), () -> persistence.commitTransition(proposal));
        workflow.executeTransition(new TransitionCommand(review, "approve", actor(runId), 1));
        workflow.activateInstance(incident);
        workflow.executeTransition(new TransitionCommand(incident, "start", actor(runId), 0));
        workflow.executeTransition(new TransitionCommand(incident, "resolve", actor(runId), 1));
        read(runId);
    }

    public void read(String runId) throws SQLException {
        validateRunId(runId);
        require("READY".equals(check()), "Schema is not ready");
        var persistence = new PostgresWorkflowPersistence(runtime, SCHEMA);
        for (String kind : List.of("review", "incident")) {
            String id = id(runId, kind);
            var instance = persistence.loadInstance(id);
            var history = persistence.loadHistory(id);
            require(instance.runtimeVersion() == 2 && instance.lifecycle() == WorkflowLifecycle.COMPLETED
                    && instance.createdAt().equals(TIME) && instance.updatedAt().equals(TIME)
                    && instance.resourceReference().equals(new ResourceReference("issue25-smoke", id))
                    && instance.definitionVersion() == 1 && history.size() == 2
                    && instance.definitionId().equals(kind.equals("review") ? "document-review" : "incident-resolution")
                    && instance.currentState().equals(kind.equals("review") ? "APPROVED" : "RESOLVED"),
                    "Durable snapshot mismatch");
            for (int i = 0; i < 2; i++) {
                var record = history.get(i);
                String[] transitions = kind.equals("review") ? new String[]{"submit", "approve"} : new String[]{"start", "resolve"};
                String[] states = kind.equals("review") ? new String[]{"DRAFT", "IN_REVIEW", "APPROVED"} : new String[]{"OPEN", "IN_PROGRESS", "RESOLVED"};
                require(record.equals(new TransitionRecord(id, transitions[i], states[i], states[i + 1], actor(runId), i + 1, TIME)),
                        "Durable history mismatch");
            }
        }
    }

    record DevelopmentAudit(String catalog, long instances, long history, long runInstances, long runHistory) {
        void report(java.io.PrintStream output) {
            output.println("DEVELOPMENT_AUDIT_PASS");
            output.println("DEVELOPMENT_CATALOG_SHA256=" + catalog);
            output.println("DEVELOPMENT_INSTANCES=" + instances);
            output.println("DEVELOPMENT_HISTORY=" + history);
            output.println("DEVELOPMENT_RUN_INSTANCES=" + runInstances);
            output.println("DEVELOPMENT_RUN_HISTORY=" + runHistory);
        }
    }

    DevelopmentAudit audit(String runId) throws Exception {
        require(development != null, "Development audit required");
        validateRunId(runId);
        WorkflowApprovedMigration.bundle(bundle);
        try (Connection c = runtime.getConnection()) {
            WorkflowSchemaBootstrap.beginReadOnly(c);
            WorkflowSchemaBootstrap.session(c, true);
            WorkflowApprovedMigration.permissions(c);
            var schemaAudit = WorkflowSchemaBootstrap.audit(c);
            require(schemaAudit != null && "postgres".equals(schemaAudit.owner()) && schemaAudit.privateAccess(),
                    "Private development schema required");
            require(WorkflowApprovedMigration.history(c).ready(), "Pinned migrations required");
            require(WorkflowApprovedMigration.EXPECTED.equals(WorkflowApprovedMigration.inventory(c)), "Inventory drift");
            require(WorkflowApprovedMigration.definitionSeed(c), "Seed drift");
            var catalog = new TreeMap<>(WorkflowApprovedMigration.snapshot(c));
            catalog.put("target_inventory", new TreeMap<>(WorkflowApprovedMigration.inventory(c)).toString());
            catalog.put("target_schema", Long.toString(schemaAudit.oid()));
            try (var st = c.createStatement(); var rows = st.executeQuery("""
                    SELECT pg_catalog.md5(pg_catalog.jsonb_agg(pg_catalog.to_jsonb(h) ORDER BY installed_rank)::text)
                    FROM dxos_wf25_smoke_01.flyway_schema_history h
                    """)) {
                require(rows.next(), "History fingerprint missing");
                catalog.put("migration_rows", rows.getString(1));
            }
            String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            long[] counts = new long[4];
            int offset = 0;
            for (String table : List.of("workflow_instances", "workflow_transition_history")) {
                try (var st = c.prepareStatement("SELECT count(*), count(*) FILTER (WHERE instance_id IN (?, ?)) FROM "
                        + schema.quoted() + "." + table)) {
                    st.setString(1, id(runId, "review"));
                    st.setString(2, id(runId, "incident"));
                    try (var rows = st.executeQuery()) {
                        require(rows.next(), "Smoke counts missing");
                        counts[offset] = rows.getLong(1);
                        counts[offset + 1] = rows.getLong(2);
                    }
                }
                offset += 2;
            }
            c.rollback();
            return new DevelopmentAudit(hash, counts[0], counts[2], counts[1], counts[3]);
        }
    }

    private static void race(Callable<Object> first, Callable<Object> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> compete(barrier, first));
            var right = workers.submit(() -> compete(barrier, second));
            List<Object> results = List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
            require(results.stream().filter(WorkflowErrorCode.VERSION_CONFLICT::equals).count() == 1
                    && results.stream().filter(r -> !(r instanceof WorkflowErrorCode)).count() == 1,
                    "Concurrent writer contract mismatch");
        }
    }

    private static Object compete(CyclicBarrier barrier, Callable<Object> operation) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            return operation.call();
        } catch (WorkflowException failure) {
            if (failure.code() != WorkflowErrorCode.VERSION_CONFLICT) throw failure;
            return failure.code();
        }
    }

    static Connection readConnection(DataSource source) throws SQLException {
        Connection connection = source.getConnection();
        try {
            require(connection.getAutoCommit(), "Unexpected ambient transaction");
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
        try (var s = connection.createStatement();
             var r = s.executeQuery("SELECT pg_catalog.current_setting('transaction_read_only'), SESSION_USER, CURRENT_USER")) {
                r.next();
            require("on".equals(r.getString(1)) && r.getString(2).equals(r.getString(3)),
                    "Preflight requires a direct role login in a read-only transaction");
            }
            return connection;
        } catch (SQLException | RuntimeException failure) {
            connection.close();
            throw failure;
        }
    }

    private static void validateRunId(String id) {
        require(id != null && id.matches("[a-z0-9][a-z0-9-]{0,39}"), "Invalid smoke run identifier");
    }

    private static String id(String run, String kind) { return "issue25-" + run + "-" + kind; }
    private static ActorReference actor(String run) { return new ActorReference("issue25-smoke-" + run); }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }

    static void requireEndpoint(String url, boolean local, String projectRef) {
        var uri = java.net.URI.create(url.substring("jdbc:".length()));
        boolean loopback = Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost());
        require(local ? loopback : projectRef.matches("[a-z0-9]{10,40}")
                && ("db." + projectRef + ".supabase.co").equals(uri.getHost()) && uri.getPort() == 5432
                && "/postgres".equals(uri.getRawPath()) && uri.getRawQuery() == null && uri.getRawFragment() == null,
                "Smoke endpoint rejected");
    }

    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.flywaydb"))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.springframework.jdbc"))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        try {
            String mode = args.length == 0 ? "check" : args[0];
            require(Set.of("check", "migrate", "smoke", "read").contains(mode), "Invalid mode");
            String url = env("WORKFLOW_SMOKE_JDBC_URL");
            var schema = new WorkflowSchema(SCHEMA);
            schema.validateUrl(url);
            boolean local = "true".equals(System.getenv("WORKFLOW_SMOKE_LOCAL_DISPOSABLE"));
            requireEndpoint(url, local, local ? "" : env("WORKFLOW_SMOKE_PROJECT_REF"));
            Properties properties = new Properties();
            properties.setProperty("currentSchema", SCHEMA + ",pg_catalog,pg_temp");
            properties.setProperty("connectTimeout", "10");
            properties.setProperty("socketTimeout", "20");
            properties.setProperty("ApplicationName", "dxos-workflow25-smoke");
            if (!local) {
                String certificate = env("WORKFLOW_SMOKE_CA_CERT");
                require(Files.isRegularFile(Path.of(certificate)), "CA certificate missing");
                properties.setProperty("sslmode", "verify-full");
                properties.setProperty("sslrootcert", certificate);

            }
            var owner = dataSource(url, properties, "MIGRATOR");
            var runtime = dataSource(url, properties, "RUNTIME");
            var runner = new WorkflowSmokeRunner(owner, runtime, Path.of(env("WORKFLOW_SMOKE_BUNDLE")));
            switch (mode) {
                case "check" -> System.out.println("PREFLIGHT_" + runner.check());
                case "migrate" -> { runner.migrate(); System.out.println("MIGRATION_PASS"); }
                case "smoke" -> { runner.smoke(args.length > 1 ? args[1] : env("WORKFLOW_SMOKE_RUN_ID")); System.out.println("SMOKE_PASS"); }
                case "read" -> { runner.read(args.length > 1 ? args[1] : env("WORKFLOW_SMOKE_RUN_ID")); System.out.println("RESTART_READ_PASS"); }
                default -> throw new IllegalArgumentException();
            }
        } catch (Exception failure) {
            System.err.println(failure instanceof WorkflowException wf ? "WORKFLOW_FAIL_" + wf.code() : "WORKFLOW_SMOKE_FAIL");
            System.exit(1);
        }
    }

    private static DriverManagerDataSource dataSource(String url, Properties properties, String kind) {
        var source = new DriverManagerDataSource(url, env("WORKFLOW_SMOKE_" + kind + "_USER"),
                env("WORKFLOW_SMOKE_" + kind + "_PASSWORD"));
        source.setConnectionProperties(properties);
        return source;
    }

    private static String env(String key) {
        String value = System.getenv(key);
        require(value != null && !value.isBlank(), "Required environment missing");
        return value;
    }
}
