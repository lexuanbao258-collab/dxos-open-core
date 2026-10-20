package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * S6.5 isolated PG17 LOGIN-role evidence (NOT production security approval).
 *
 * Unlike SET ROLE tests, this uses real password-authenticated PostgreSQL LOGIN.
 * Only an explicitly approved localhost "stage6_disposable_*" database is supported.
 * A random schema and one random non-privileged LOGIN role are COMMITTED temporarily
 * so the second connection can authenticate, then deleted in @AfterEach. The ephemeral
 * credentials never appear in test output. No staging, Identity, JWT, HTTP, or Flyway.
 *
 * Tests ONE currently implemented candidate CREATE SQL function, NOT six approved
 * production signatures, safe role provisioning, or resilience to stolen DB credentials.
 */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/stage6_disposable_[a-zA-Z0-9_]+")
class Stage6RuntimeLoginPrivilegeIntegrationTest {
    private Connection administrator;
    private String schema;
    private String runtimeRole;
    private String runtimePassword;
    private String jdbcUrl;

    @BeforeEach
    void prepare() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")));
        assumeTrue("YES".equals(System.getenv("STAGE6_LOGIN_ROLE_TEST_APPROVAL")),
                "A real LOGIN test additionally requires an explicit opt-in");
        jdbcUrl = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        assumeTrue(("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
                && uri.getPath().matches("/stage6_disposable_[a-zA-Z0-9_]+"));
        String adminUser = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        String adminPassword = System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD");
        assumeTrue("stage6_test".equals(adminUser) && adminPassword != null,
                "Only the expected disposable-container owner may provision a LOGIN");
        administrator = DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
        administrator.setAutoCommit(false);
        try (var stmt = administrator.createStatement();
             var rows = stmt.executeQuery(
                     "SELECT current_setting('server_version_num')::integer,current_database(),current_user")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isBetween(170000, 179999);
            assertThat(rows.getString(2)).matches("stage6_disposable_[a-zA-Z0-9_]+");
            assertThat(rows.getString(3)).isEqualTo("stage6_test");
        }

        schema = "s6_login_" + UUID.randomUUID().toString().replace("-", "");
        runtimeRole = "s6_rt_" + UUID.randomUUID().toString().replace("-", "");
        byte[] entropy = new byte[32];
        new SecureRandom().nextBytes(entropy);
        runtimePassword = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);

        // Identifiers are generated from a fixed safe alphabet, not provided by a caller.
        execute(administrator, "CREATE SCHEMA " + schema);
        String placeholder = "$" + "{flyway:defaultSchema}";
        for (String file : new String[] {
                "src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_create_candidate.sql"
        }) {
            execute(administrator, Files.readString(Path.of(file)).replace(placeholder, schema));
        }

        execute(administrator, "INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a')");
        execute(administrator, "INSERT INTO " + schema
                + ".tenant_memberships(tenant_id,actor_id,active)"
                + " VALUES('tenant-a','member-a',true)");
        execute(administrator, "INSERT INTO " + schema
                + ".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle)"
                + " VALUES('item','res-a','tenant-a','member-a','ACTIVE')");
        execute(administrator, "INSERT INTO " + schema
                + ".resource_acl(tenant_id,resource_type,resource_id,actor_id,operation,granted)"
                + " VALUES('tenant-a','item','res-a','member-a','workflow:create',true)");
        execute(administrator, "INSERT INTO " + schema
                + ".workflow_definitions(definition_id,definition_version,initial_state)"
                + " VALUES('publication',7,'draft')");
        execute(administrator, "INSERT INTO " + schema
                + ".workflow_definition_states(definition_id,definition_version,state_id)"
                + " VALUES('publication',7,'draft')");
        execute(administrator, "INSERT INTO " + schema
                + ".workflow_definition_bindings(tenant_id,resource_type,definition_id,definition_version,active)"
                + " VALUES('tenant-a','item','publication',7,true)");

        execute(administrator, "CREATE ROLE " + runtimeRole + " LOGIN PASSWORD '" + runtimePassword
                + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT");
        execute(administrator, "REVOKE ALL ON SCHEMA " + schema + " FROM PUBLIC");
        execute(administrator, "REVOKE ALL ON ALL FUNCTIONS IN SCHEMA " + schema + " FROM PUBLIC");
        execute(administrator, "GRANT USAGE ON SCHEMA " + schema + " TO " + runtimeRole);
        execute(administrator, "GRANT EXECUTE ON FUNCTION " + schema
                + ".wf_port_create_instance(text,boolean,jsonb) TO " + runtimeRole);

        // Commit only the disposable fixture/role. Each test connects independently.
        administrator.commit();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (administrator == null) return;
        try {
            administrator.rollback();
            if (schema != null && schema.matches("s6_login_[a-f0-9]{32}")) {
                execute(administrator, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
            if (runtimeRole != null && runtimeRole.matches("s6_rt_[a-f0-9]{32}")) {
                execute(administrator, "DROP ROLE IF EXISTS " + runtimeRole);
            }
            administrator.commit();
        } finally {
            administrator.close();
        }
    }

    @Test
    void independentLoginCannotReadWriteOwnOrEscalateProtectedDatabase() throws Exception {
        try (Connection runtime = login()) {
            assertThat(text(runtime, "SELECT current_user")).isEqualTo(runtimeRole);
            assertThat(text(runtime, "SELECT session_user")).isEqualTo(runtimeRole);
            assertThat(number(runtime, "SELECT (SELECT rolsuper FROM pg_roles WHERE rolname=current_user)::int"))
                    .isZero();
            for (String name : new String[] {
                    "tenant_guards", "tenant_memberships", "resource_registry",
                    "resource_acl", "workflow_definition_bindings", "workflow_policy_audit",
                    "workflow_definitions", "workflow_definition_states",
                    "workflow_instances", "workflow_transition_history"
            }) {
                rejected(runtime, "SELECT * FROM " + schema + "." + name, "42501");
            }
            rejected(runtime, "UPDATE " + schema + ".resource_acl SET granted=false", "42501");
            rejected(runtime, "DELETE FROM " + schema + ".tenant_memberships", "42501");
            rejected(runtime, "INSERT INTO " + schema + ".workflow_instances(instance_id)"
                    + " VALUES('forged')", "42501");
            rejected(runtime, "CREATE TABLE " + schema + ".unapproved(id int)", "42501");
            rejected(runtime, "CREATE ROLE s6_forbidden_escalation NOLOGIN", "42501");
            rejected(runtime, "SET ROLE stage6_test", "42501");
        }
    }

    @Test
    void actualLoginCanExecuteOnlyTheAllowedSqlFunction() throws Exception {
        String createdId = "wf_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection runtime = login()) {
            assertThat(text(runtime, "SELECT current_user")).isEqualTo(runtimeRole);
            try (PreparedStatement query = runtime.prepareStatement(
                    "SELECT p.proname,"
                    + "has_function_privilege(current_user,p.oid,'EXECUTE') "
                    + "FROM pg_catalog.pg_proc p "
                    + "JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace "
                    + "WHERE n.nspname=? ORDER BY p.proname")) {
                query.setString(1, schema);
                int allowed = 0;
                int denied = 0;
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        if ("wf_port_create_instance".equals(rows.getString(1))) {
                            assertThat(rows.getBoolean(2)).isTrue();
                            allowed++;
                        } else {
                            assertThat(rows.getBoolean(2)).isFalse();
                            denied++;
                        }
                    }
                }
                assertThat(allowed).isEqualTo(1);
                assertThat(denied).isGreaterThanOrEqualTo(2);
            }
            assertThat(callCreate(runtime, createdId, "member-a")).isEqualTo("SNAPSHOT");
            assertThat(callCreate(runtime,
                    "wf_" + UUID.randomUUID().toString().replace("-", ""), "unknown"))
                    .isEqualTo("HIDDEN");
            rejected(runtime, "SELECT * FROM " + schema + ".workflow_instances", "42501");
            runtime.commit();
        }
        assertThat(number(administrator, "SELECT count(*) FROM " + schema
                + ".workflow_instances WHERE instance_id='" + createdId + "'")).isEqualTo(1);
        administrator.rollback();
    }

    private String callCreate(Connection c, String id, String actor) throws SQLException {
        String proposal = """
                {"instance_id":"%s","definition_id":"publication","definition_version":7,
                 "resource_type":"item","resource_id":"res-a","current_state":"draft",
                 "runtime_version":0,"lifecycle":"NOT_STARTED","created_at_seconds":0,
                 "created_at_nanos":0,"updated_at_seconds":0,"updated_at_nanos":0}
                """.formatted(id);
        try (PreparedStatement p = c.prepareStatement(
                "SELECT outcome FROM " + schema + ".wf_port_create_instance(?,?,?::jsonb)")) {
            p.setString(1, actor);
            p.setBoolean(2, true);
            p.setString(3, proposal);
            try (ResultSet rows = p.executeQuery()) {
                assertThat(rows.next()).isTrue();
                String outcome = rows.getString(1);
                assertThat(rows.next()).isFalse();
                return outcome;
            }
        }
    }

    private void rejected(Connection c, String sql, String expectedSqlstate) throws Exception {
        Savepoint save = c.setSavepoint();
        try {
            execute(c, sql);
            throw new AssertionError("Expected permission denied");
        } catch (SQLException failure) {
            assertThat(failure.getSQLState()).isEqualTo(expectedSqlstate);
        } finally {
            c.rollback(save);
            c.releaseSavepoint(save);
        }
    }

    private Connection login() throws SQLException {
        Connection c = DriverManager.getConnection(jdbcUrl, runtimeRole, runtimePassword);
        c.setAutoCommit(false);
        return c;
    }

    private int number(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var result = s.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private String text(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var result = s.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private void execute(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement()) { s.execute(sql); }
    }
}
