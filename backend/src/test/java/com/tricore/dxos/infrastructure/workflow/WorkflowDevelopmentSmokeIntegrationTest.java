package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "WORKFLOW_AUDIT_LOCAL_JDBC_URL", matches = ".+")
class WorkflowDevelopmentSmokeIntegrationTest {
    @TempDir Path temporary;
    DataSource admin;
    Map<String, String> environment;
    boolean created;
    String probe = "wf25_dev_probe_" + UUID.randomUUID().toString().replace("-", "");
    static final String S = WorkflowSmokeRunner.SCHEMA;

    @BeforeEach void setup() throws Exception {
        String url = WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_JDBC_URL");
        if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/postgres")) {
            throw new IllegalArgumentException("Disposable loopback database required");
        }
        var uri = java.net.URI.create(url.substring(5));
        Path bundle = Files.createDirectory(temporary.resolve("bundle"));
        for (String name : new String[]{"V3__create_workflow_persistence.sql", "V4__seed_workflow_definitions.sql"}) {
            Files.copy(Path.of("src/main/resources/db/migration", name), bundle.resolve(name));
        }
        environment = new HashMap<>();
        environment.put("WORKFLOW_DEV_INTEGRATION_OPT_IN", WorkflowDevelopmentSmokeRunner.OPT_IN);
        environment.put("WORKFLOW_DEV_APPROVED_SCHEMA", S);
        environment.put("WORKFLOW_DEV_WRITE_APPROVAL", S);
        environment.put("WORKFLOW_DEV_LOCAL_DISPOSABLE", "true");
        environment.put("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE", "true");
        environment.put("WF25_DISPOSABLE_TEST", "1");
        environment.put("WORKFLOW_DEV_BUNDLE", bundle.toString());
        environment.put("DB_HOST", "localhost");
        environment.put("DB_PORT", Integer.toString(uri.getPort()));
        environment.put("DB_NAME", "postgres");
        environment.put("DB_USER", "postgres");
        environment.put("DB_PASSWORD", WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_DB_PASSWORD"));
        environment.put("PGSSLROOTCERT", WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_CA_CERT"));
        var source = new DriverManagerDataSource("jdbc:postgresql://localhost:" + uri.getPort() + "/postgres",
                "postgres", environment.get("DB_PASSWORD"));
        Properties properties = new Properties();
        properties.setProperty("sslmode", "verify-full");
        properties.setProperty("sslrootcert", environment.get("PGSSLROOTCERT"));
        source.setConnectionProperties(properties);
        admin = source;
        try (Connection c = admin.getConnection(); var statement = c.createStatement()) {
            try (var rows = statement.executeQuery("SELECT pg_catalog.current_setting('dxos.wf25_disposable', true)")) {
                assertThat(rows.next() && "on".equals(rows.getString(1))).isTrue();
            }
            statement.execute("CREATE SCHEMA " + S + " AUTHORIZATION postgres");
            created = true;
            statement.execute("CREATE TABLE public." + probe + "(value text)");
            statement.execute("INSERT INTO public." + probe + " VALUES ('preserved')");
        }
    }

    @AfterEach void cleanup() throws Exception {
        if (created) try (Connection c = admin.getConnection(); var statement = c.createStatement()) {
            statement.execute("DROP SCHEMA " + S + " CASCADE");
            statement.execute("DROP TABLE IF EXISTS public." + probe);
        }
    }

    @Test void postgresMigrationSmokeAndNewJvmReadStayInsideApprovedSchema() throws Exception {
        String outside = outsideDigest();
        environment.remove("WORKFLOW_DEV_WRITE_APPROVAL");
        assertThat(WorkflowDevelopmentSmokeRunner.prepare(environment, "check").check()).isEqualTo("EMPTY");
        assertThat(scalar("SELECT count(*) FROM pg_catalog.pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
        environment.put("WORKFLOW_DEV_WRITE_APPROVAL", S);
        var runner = WorkflowDevelopmentSmokeRunner.prepare(environment, "migrate");
        assertChild(WorkflowDevelopmentSmokeRunner.class, environment, 0,
                "DEVELOPMENT_MIGRATION_PASS", "--development-integration", "migrate");
        assertThat(runner.check()).isEqualTo("READY");
        assertThat(count("workflow_definitions")).isEqualTo(2);
        assertThat(count("flyway_schema_history")).isEqualTo(2);
        assertChild(WorkflowDevelopmentSmokeRunner.class, environment, 0,
                "DEVELOPMENT_SMOKE_PASS", "--development-integration", "smoke", "development-local");
        assertThat(count("workflow_instances")).isEqualTo(2);
        assertThat(count("workflow_transition_history")).isEqualTo(4);
        assertThatThrownBy(() -> runner.smoke("development-local")).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("workflow_instances")).isEqualTo(2);
        var readEnvironment = new HashMap<>(environment);
        readEnvironment.remove("WORKFLOW_DEV_WRITE_APPROVAL");
        assertChild(WorkflowDevelopmentSmokeRunner.class, readEnvironment, 0,
                "DEVELOPMENT_RESTART_READ_PASS", "--development-integration", "read", "development-local");
        assertThat(outsideDigest()).isEqualTo(outside);
    }

    @Test void productionRunnerRejectsSamePostgresOwnerEvenWithDevelopmentEnvironment() throws Exception {
        WorkflowDevelopmentSmokeRunner.prepare(environment, "migrate").migrate();
        var production = new HashMap<>(environment);
        production.put("WORKFLOW_SMOKE_JDBC_URL", "jdbc:postgresql://localhost:" + environment.get("DB_PORT") + "/postgres");
        production.put("WORKFLOW_SMOKE_LOCAL_DISPOSABLE", "true");
        production.put("WORKFLOW_SMOKE_BUNDLE", environment.get("WORKFLOW_DEV_BUNDLE"));
        for (String kind : new String[]{"MIGRATOR", "RUNTIME"}) {
            production.put("WORKFLOW_SMOKE_" + kind + "_USER", "postgres");
            production.put("WORKFLOW_SMOKE_" + kind + "_PASSWORD", environment.get("DB_PASSWORD"));
        }
        assertChild(WorkflowSmokeRunner.class, production, 1, "WORKFLOW_SMOKE_FAIL", "smoke", "production-denied");
        assertThat(count("workflow_instances")).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"opt-in", "schema", "write-approval", "local-profile"})
    void missingAuthorizationFailsBeforeSchemaWrites(String missing) throws Exception {
        var denied = new HashMap<>(environment);
        denied.remove(switch (missing) {
            case "opt-in" -> "WORKFLOW_DEV_INTEGRATION_OPT_IN";
            case "schema" -> "WORKFLOW_DEV_APPROVED_SCHEMA";
            case "write-approval" -> "WORKFLOW_DEV_WRITE_APPROVAL";
            default -> "WF25_DISPOSABLE_TEST";
        });
        var errors = new ByteArrayOutputStream();
        assertThat(WorkflowDevelopmentSmokeRunner.run(denied, new String[]{"--development-integration", "migrate"},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors))).isEqualTo(1);
        assertThat(errors.toString(StandardCharsets.UTF_8).trim()).isEqualTo("WORKFLOW_DEVELOPMENT_SMOKE_FAIL");
        assertThat(scalar("SELECT count(*) FROM pg_catalog.pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"wrong-ca", "wrong-hostname"})
    void actualTlsFailuresPreventMigration(String failure) throws Exception {
        if (failure.equals("wrong-ca")) {
            environment.put("PGSSLROOTCERT", WorkflowIsolationIntegrationTest.env("WORKFLOW_AUDIT_LOCAL_WRONG_CA_CERT"));
        } else environment.put("DB_HOST", "127.0.0.1");
        assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "migrate").migrate())
                .isInstanceOf(java.sql.SQLException.class);
        assertThat(scalar("SELECT count(*) FROM pg_catalog.pg_class WHERE relnamespace='" + S + "'::regnamespace")).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"public-access", "unexpected-table", "wrong-owner"})
    void unreviewedSchemaFailsBeforeMigration(String failure) throws Exception {
        try (Connection c = admin.getConnection(); var statement = c.createStatement()) {
            statement.execute(switch (failure) {
                case "public-access" -> "GRANT USAGE ON SCHEMA " + S + " TO PUBLIC";
                case "unexpected-table" -> "CREATE TABLE " + S + ".unexpected(id int)";
                default -> "ALTER SCHEMA " + S + " OWNER TO workflow_test";
            });
        }
        assertThatThrownBy(() -> WorkflowDevelopmentSmokeRunner.prepare(environment, "migrate").migrate())
                .isInstanceOfAny(java.sql.SQLException.class, IllegalArgumentException.class);
        assertThat(scalar("SELECT count(*) FROM pg_catalog.pg_class WHERE relnamespace='" + S
                + "'::regnamespace AND relname='flyway_schema_history'")).isZero();
    }

    @Test void bundleAndCatalogDriftAreRejectedBeforeSmokeWrites() throws Exception {
        var runner = WorkflowDevelopmentSmokeRunner.prepare(environment, "migrate");
        runner.migrate();
        try (Connection c = admin.getConnection(); var statement = c.createStatement()) {
            statement.execute("UPDATE " + S + ".flyway_schema_history SET checksum=checksum+1 WHERE version='4'");
        }
        assertThatThrownBy(() -> runner.smoke("checksum-denied")).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("workflow_instances")).isZero();
        Files.writeString(Path.of(environment.get("WORKFLOW_DEV_BUNDLE"), "V4__seed_workflow_definitions.sql"), "-- changed");
        assertThatThrownBy(() -> runner.smoke("bundle-denied"))
                .isInstanceOf(java.sql.SQLException.class).hasMessage("Workflow connection rejected");
        assertThat(count("workflow_instances")).isZero();
    }

    private void assertChild(Class<?> main, Map<String, String> variables, int exit, String result, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("workflow.test.runtimeClasspath", System.getProperty("java.class.path")));
        command.add(main.getName());
        command.addAll(java.util.List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().keySet().removeIf(key -> key.startsWith("WORKFLOW_DEV_") || key.startsWith("WORKFLOW_SMOKE_"));
        builder.environment().putAll(variables);
        var process = builder.start();
        try {
            assertThat(process.waitFor(45, TimeUnit.SECONDS)).isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).isEqualTo(exit);
            assertThat(output).contains(result).doesNotContain(environment.get("DB_PASSWORD"));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    private long count(String table) throws Exception { return scalar("SELECT count(*) FROM " + S + "." + table); }
    private long scalar(String sql) throws Exception {
        try (Connection c = admin.getConnection(); var statement = c.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getLong(1);
        }
    }
    private String outsideDigest() throws Exception {
        try (Connection c = admin.getConnection(); var statement = c.createStatement(); var rows = statement.executeQuery(
                "SELECT concat((SELECT string_agg(r::text,',' ORDER BY oid) FROM pg_catalog.pg_roles r),"
                + "(SELECT string_agg(m::text,',' ORDER BY roleid,member,grantor) FROM pg_catalog.pg_auth_members m),"
                + "(SELECT string_agg(d::text,',' ORDER BY oid) FROM pg_catalog.pg_default_acl d),"
                + "(SELECT value FROM public." + probe + "))")) {
            rows.next(); return rows.getString(1);
        }
    }
}
