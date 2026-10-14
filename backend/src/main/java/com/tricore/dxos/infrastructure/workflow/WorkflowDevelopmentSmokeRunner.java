package com.tricore.dxos.infrastructure.workflow;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** One-time operator integration entry point; never registered as a Spring bean. */
public final class WorkflowDevelopmentSmokeRunner {
    static final String OPT_IN = "issue-25-postgres-development-only";
    private WorkflowDevelopmentSmokeRunner() {}

    static WorkflowSmokeRunner prepare(Map<String, String> environment, String mode) {
        require(OPT_IN.equals(environment.get("WORKFLOW_DEV_INTEGRATION_OPT_IN")), "Development opt-in required");
        require(Set.of("check", "migrate", "smoke", "read", "audit").contains(mode), "Invalid development mode");
        require(WorkflowSmokeRunner.SCHEMA.equals(environment.get("WORKFLOW_DEV_APPROVED_SCHEMA")), "Approved schema required");
        if (Set.of("migrate", "smoke").contains(mode)) {
            require(WorkflowSmokeRunner.SCHEMA.equals(environment.get("WORKFLOW_DEV_WRITE_APPROVAL")), "Explicit write approval required");
        }
        boolean local = "true".equals(environment.get("WORKFLOW_DEV_LOCAL_DISPOSABLE"));
        require(local
                ? "1".equals(environment.get("WF25_DISPOSABLE_TEST"))
                    && "true".equals(environment.get("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE"))
                : !environment.containsKey("WORKFLOW_DEV_LOCAL_DISPOSABLE")
                    && !environment.containsKey("WF25_DISPOSABLE_TEST")
                    && !environment.containsKey("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE"), "Disposable profile rejected");
        require("postgres".equals(value(environment, "DB_USER")) && "postgres".equals(value(environment, "DB_NAME")),
                "Existing postgres operator required");
        String host = value(environment, "DB_HOST");
        String port = value(environment, "DB_PORT");
        require(port.matches("[0-9]{1,5}") && Integer.parseInt(port) > 0 && Integer.parseInt(port) <= 65535,
                "Database port rejected");
        require(host.matches(local ? "localhost|127\\.0\\.0\\.1" : "db\\.[a-z0-9]{10,40}\\.supabase\\.co"),
                "Direct endpoint required");
        String url = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        WorkflowSmokeRunner.requireEndpoint(url, local, local ? "" : value(environment, "WORKFLOW_DEV_PROJECT_REF"));
        String certificate = value(environment, "PGSSLROOTCERT");
        require(Files.isRegularFile(Path.of(certificate)), "Trusted CA required");
        Properties properties = new Properties();
        properties.setProperty("currentSchema", WorkflowSmokeRunner.SCHEMA + ",pg_catalog,pg_temp");
        properties.setProperty("sslmode", "verify-full");
        properties.setProperty("sslrootcert", certificate);
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "20");
        properties.setProperty("ApplicationName", "dxos-wf25-development-integration");
        if (Set.of("check", "read", "audit").contains(mode)) properties.setProperty("readOnly", "true");
        properties.setProperty("options", "-c lock_timeout=5s -c statement_timeout=30s");
        var source = new DriverManagerDataSource(url, "postgres", value(environment, "DB_PASSWORD"));
        source.setConnectionProperties(properties);
        return WorkflowSmokeRunner.forDevelopment(source, Path.of(value(environment, "WORKFLOW_DEV_BUNDLE")),
                new Authorization(local, Set.of("migrate", "smoke").contains(mode)));
    }

    static final class Authorization {
        private final boolean local;
        private final boolean writesApproved;
        private Authorization(boolean local, boolean writesApproved) {
            this.local = local;
            this.writesApproved = writesApproved;
        }

        void requireWrites() { require(writesApproved, "Development write approval required"); }

        void verify(Connection connection) {
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                try (var rows = statement.executeQuery("""
                        SELECT current_user = 'postgres' AND session_user = 'postgres',
                               pg_catalog.current_database() = 'postgres',
                               (SELECT ssl FROM pg_catalog.pg_stat_ssl WHERE pid = pg_catalog.pg_backend_pid()),
                               pg_catalog.current_setting('dxos.wf25_disposable', true) = 'on',
                               n.nspowner = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = 'postgres'),
                               NOT EXISTS (SELECT FROM pg_catalog.aclexplode(
                                   COALESCE(n.nspacl, pg_catalog.acldefault('n', n.nspowner))) a
                                   WHERE a.grantee <> n.nspowner),
                               NOT EXISTS (SELECT FROM pg_catalog.pg_roles r
                                   WHERE r.rolname IN ('anon','authenticated','service_role')
                                   AND (pg_catalog.has_schema_privilege(r.oid, n.oid, 'USAGE')
                                     OR pg_catalog.has_schema_privilege(r.oid, n.oid, 'CREATE'))),
                               NOT EXISTS (SELECT FROM pg_catalog.pg_class c
                                   WHERE c.relnamespace = n.oid AND c.relowner <> n.nspowner)
                        FROM pg_catalog.pg_namespace n WHERE n.nspname = 'dxos_wf25_smoke_01'
                        """)) {
                    require(rows.next() && rows.getBoolean(1) && rows.getBoolean(2) && rows.getBoolean(3)
                            && (!local || rows.getBoolean(4)) && rows.getBoolean(5) && rows.getBoolean(6)
                            && rows.getBoolean(7) && rows.getBoolean(8), "Private development schema or TLS rejected");
                }
            } catch (SQLException failure) {
                throw new IllegalArgumentException("Development catalog verification failed");
            }
        }
    }

    static int run(Map<String, String> environment, String[] args, PrintStream output, PrintStream errors) {
        try {
            require(args.length >= 1 && args.length <= 3 && "--development-integration".equals(args[0]),
                    "Explicit development command required");
            String mode = args.length > 1 ? args[1] : "check";
            require(args.length < 3 || Set.of("smoke", "read", "audit").contains(mode), "Unexpected run identifier");
            var runner = prepare(environment, mode);
            switch (mode) {
                case "check" -> output.println("DEVELOPMENT_PREFLIGHT_" + runner.check());
                case "migrate" -> { runner.migrate(); output.println("DEVELOPMENT_MIGRATION_PASS"); }
                case "smoke" -> {
                    runner.smoke(args.length == 3 ? args[2] : value(environment, "WORKFLOW_DEV_RUN_ID"));
                    output.println("DEVELOPMENT_SMOKE_PASS");
                }
                case "read" -> {
                    runner.read(args.length == 3 ? args[2] : value(environment, "WORKFLOW_DEV_RUN_ID"));
                    output.println("DEVELOPMENT_RESTART_READ_PASS");
                }
                case "audit" -> runner.audit(args.length == 3 ? args[2] : value(environment, "WORKFLOW_DEV_RUN_ID")).report(output);
                default -> throw new IllegalArgumentException("Invalid development mode");
            }
            return 0;
        } catch (Exception failure) {
            errors.println(failure instanceof com.tricore.dxos.core.workflow.WorkflowException workflow
                    ? "WORKFLOW_DEVELOPMENT_FAIL_" + workflow.code() : "WORKFLOW_DEVELOPMENT_SMOKE_FAIL");
            return 1;
        }
    }

    public static void main(String[] args) {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.flywaydb"))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.springframework.jdbc"))
                .setLevel(ch.qos.logback.classic.Level.OFF);
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        System.exit(run(System.getenv(), args, System.out, System.err));
    }

    private static String value(Map<String, String> environment, String key) {
        String value = environment.get(key);
        require(value != null && !value.isBlank(), "Required development environment missing");
        return value;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }
}
