package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stage 6.5 candidate: exercise effective permissions with SET ROLE to an ephemeral
 * NOLOGIN role inside a single rollback-only transaction on disposable PostgreSQL 17.
 * Tests the existing ONE protected CREATE signature, not all six Workflow functions.
 * NO persistent roles, Flyway, production grants, Identity changes or Workflow HTTP.
 *
 * SET ROLE is not proof of an independently authenticated runtime LOGIN;
 * such negative tests plus schema owner/default privileges require separate review.
 */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/stage6_disposable_[a-zA-Z0-9_]+")
class Stage6LeastPrivilegeCandidateIntegrationTest {
    private Connection db;
    private String schema;
    private String runtimeRole;

    @BeforeEach
    void setup() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")));
        String url = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        URI target = URI.create(url.substring(5));
        assumeTrue(("localhost".equals(target.getHost()) || "127.0.0.1".equals(target.getHost()))
                && target.getPath().matches("/stage6_disposable_[a-zA-Z0-9_]+"));
        String user = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        String password = System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD");
        assumeTrue(user != null && !user.isBlank() && password != null);

        db = DriverManager.getConnection(url, user, password);
        db.setAutoCommit(false);
        assertThat(scalar("SELECT current_setting('server_version_num')::integer"))
                .isBetween(170000, 179999);
        schema = "s6_priv_" + UUID.randomUUID().toString().replace("-", "");
        runtimeRole = "s6_rt_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        String placeholder = "$" + "{flyway:defaultSchema}";
        for (String file : new String[] {
                "src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_create_candidate.sql"
        }) {
            execute(Files.readString(Path.of(file)).replace(placeholder, schema));
        }
        execute("CREATE ROLE " + runtimeRole + " NOLOGIN NOINHERIT");
        execute("REVOKE ALL ON SCHEMA " + schema + " FROM PUBLIC");
        execute("GRANT USAGE ON SCHEMA " + schema + " TO " + runtimeRole);
        execute("GRANT EXECUTE ON FUNCTION " + schema
                + ".wf_port_create_instance(text,boolean,jsonb) TO " + runtimeRole);

        // Owner-only synthetic fixture. The runtime role never receives DML.
        execute("INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a')");
        execute("INSERT INTO " + schema
                + ".tenant_memberships(tenant_id,actor_id,active)"
                + " VALUES('tenant-a','member-a',true)");
        execute("INSERT INTO " + schema
                + ".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle)"
                + " VALUES('item','res-a','tenant-a','member-a','ACTIVE')");
        execute("INSERT INTO " + schema
                + ".resource_acl(tenant_id,resource_type,resource_id,actor_id,operation,granted)"
                + " VALUES('tenant-a','item','res-a','member-a','workflow:create',true)");
        execute("INSERT INTO " + schema
                + ".workflow_definitions(definition_id,definition_version,initial_state)"
                + " VALUES('publication',7,'draft')");
        execute("INSERT INTO " + schema
                + ".workflow_definition_states(definition_id,definition_version,state_id)"
                + " VALUES('publication',7,'draft')");
        execute("INSERT INTO " + schema
                + ".workflow_definition_bindings(tenant_id,resource_type,definition_id,definition_version,active)"
                + " VALUES('tenant-a','item','publication',7,true)");
        execute("SET ROLE " + runtimeRole);
        assertThat(text("SELECT current_user")).isEqualTo(runtimeRole);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (db != null) {
            try {
                try { db.rollback(); }
                finally { db.close(); }
            } catch (SQLException ignored) {
                // The transaction is disposable; connection close also rolls back.
                throw ignored;
            }
        }
    }

    @Test
    void runtimeCannotReadOrModifyProtectedV3OrV5Tables() throws Exception {
        for (String name : new String[] {
                "tenant_guards", "tenant_memberships", "resource_registry",
                "resource_acl", "workflow_definition_bindings", "workflow_policy_audit",
                "workflow_instances", "workflow_transition_history",
                "workflow_definitions", "workflow_definition_states"
        }) {
            rejected("SELECT * FROM " + schema + "." + name, "42501");
        }
        rejected("UPDATE " + schema + ".resource_acl SET granted=false", "42501");
        rejected("DELETE FROM " + schema + ".tenant_memberships", "42501");
        rejected("TRUNCATE TABLE " + schema + ".tenant_guards", "42501");
        rejected("INSERT INTO " + schema + ".workflow_instances (instance_id) VALUES ('wrong')", "42501");
        rejected("CREATE TABLE " + schema + ".evil(id integer)", "42501");
    }

    @Test
    void runtimeCanCallReviewedCreateEntrypointWithoutDirectDml() throws Exception {
        String id = "wf_" + UUID.randomUUID().toString().replace("-", "");
        String json = """
                {"instance_id":"%s","definition_id":"publication","definition_version":7,
                 "resource_type":"item","resource_id":"res-a","current_state":"draft",
                 "runtime_version":0,"lifecycle":"NOT_STARTED","created_at_seconds":0,
                 "created_at_nanos":0,"updated_at_seconds":0,"updated_at_nanos":0}
                """.formatted(id);
        try (PreparedStatement stmt = db.prepareStatement(
                "SELECT outcome,body FROM " + schema + ".wf_port_create_instance(?,?,?::jsonb)")) {
            stmt.setString(1, "member-a");
            stmt.setBoolean(2, true);
            stmt.setString(3, json);
            try (ResultSet rows = stmt.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("SNAPSHOT");
                assertThat(rows.getString(2)).contains(id);
                assertThat(rows.next()).isFalse();
            }
        }
        rejected("SELECT * FROM " + schema + ".workflow_instances", "42501");
        rejected("UPDATE " + schema + ".resource_acl SET granted=false", "42501");
        // The owner, not the runtime role, can inspect test-only fixture state.
        execute("RESET ROLE");
        assertThat(scalar("SELECT count(*) FROM " + schema
                + ".workflow_instances WHERE instance_id='" + id + "'")).isEqualTo(1);
    }

    @Test
    void runtimeHasNoTriggerFunctionExecuteOrRoleEscalation() throws Exception {
        assertThat(scalar("SELECT has_function_privilege(current_user, '"
                + schema + ".wf_port_create_instance(text,boolean,jsonb)', 'EXECUTE')::int"))
                .isEqualTo(1);
        assertThat(scalar("SELECT has_function_privilege(current_user, '"
                + schema + ".stage6_policy_immutability_guard()', 'EXECUTE')::int"))
                .isZero();
        // Cannot CREATE roles as the effective runtime role. SET ROLE itself is not
        // a useful escalation test here: the test connection's session_user is a
        // disposable superuser, unlike a real runtime LOGIN.
        rejected("CREATE ROLE s6_escalate_probe NOLOGIN", "42501");
    }

    private void rejected(String sql, String expectedSqlState) throws Exception {
        Savepoint savepoint = db.setSavepoint();
        try {
            try {
                execute(sql);
                throw new AssertionError("Expected SQL privilege denial: " + sql);
            } catch (SQLException e) {
                assertThat(e.getSQLState()).isEqualTo(expectedSqlState);
            }
        } finally {
            db.rollback(savepoint);
            db.releaseSavepoint(savepoint);
        }
    }

    private int scalar(String sql) throws SQLException {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    private String text(String sql) throws SQLException {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
