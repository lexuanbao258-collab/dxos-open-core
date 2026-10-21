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
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test-only Stage 6.7 guarded ACTIVATE / TRANSITION functions.
 * This proves SQL candidate semantics on one explicitly approved disposable PG17
 * transaction; it does NOT prove production JDBC, real runtime LOGIN, revocation
 * races, HTTP or Keycloak. Every schema/fixture is rolled back in @AfterEach.
 */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):55427/stage6_disposable_test")
class Stage6GuardedActionsCandidateIntegrationTest {
    private Connection db;
    private String schema;

    @BeforeEach
    void prepare() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")));
        assumeTrue("true".equals(System.getenv("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE")));
        String url = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        URI target = URI.create(url.substring("jdbc:".length()));
        assumeTrue(("localhost".equals(target.getHost()) || "127.0.0.1".equals(target.getHost()))
                && target.getPort() == 55427 && "/stage6_disposable_test".equals(target.getPath()));
        String user = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        String password = System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD");
        assumeTrue("stage6_test".equals(user) && password != null);

        db = DriverManager.getConnection(url, user, password);
        db.setAutoCommit(false);
        assertThat(number("SELECT current_setting('server_version_num')::integer"))
                .isBetween(170000, 179999);
        assertThat(text("SELECT current_database()")).isEqualTo("stage6_disposable_test");
        assertThat(text("SELECT current_user")).isEqualTo("stage6_test");

        schema = "s6_actions_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE SCHEMA " + schema);
        for (String file : new String[] {
                "src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_actions_candidate.sql"
        }) {
            sql(Files.readString(Path.of(file))
                    .replace("$" + "{flyway:defaultSchema}", schema));
        }
        sql("INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a'),('tenant-b')");
        sql("INSERT INTO " + schema + ".tenant_memberships(tenant_id,actor_id,active)"
                + " VALUES('tenant-a','alice',true),('tenant-b','bob',true)");
        sql("INSERT INTO " + schema + ".resource_registry"
                + " (resource_type,resource_id,tenant_id,owner_actor_id,lifecycle)"
                + " VALUES('item','r1','tenant-a','alice','ACTIVE')");
        sql("INSERT INTO " + schema + ".resource_acl"
                + " (tenant_id,resource_type,resource_id,actor_id,operation,granted)"
                + " VALUES('tenant-a','item','r1','alice','workflow:activate',true),"
                + " ('tenant-a','item','r1','alice','workflow:transition',true)");
        sql("INSERT INTO " + schema + ".workflow_definitions"
                + " (definition_id,definition_version,initial_state) VALUES('ticket',1,'new')");
        sql("INSERT INTO " + schema + ".workflow_definition_states"
                + " (definition_id,definition_version,state_id)"
                + " VALUES('ticket',1,'new'),('ticket',1,'assigned'),('ticket',1,'done')");
        sql("INSERT INTO " + schema + ".workflow_definition_transitions"
                + " (definition_id,definition_version,transition_id,source_state,target_state)"
                + " VALUES('ticket',1,'assign','new','assigned'),"
                + " ('ticket',1,'resolve','assigned','done')");
        sql("INSERT INTO " + schema + ".workflow_instances"
                + " (instance_id,definition_id,definition_version,resource_type,resource_id,"
                + " current_state,runtime_version,lifecycle,created_at_seconds,created_at_nanos,"
                + " updated_at_seconds,updated_at_nanos)"
                + " VALUES('instance-a','ticket',1,'item','r1','new',0,'NOT_STARTED',10,0,10,0)");
    }

    @AfterEach
    void rollback() throws Exception {
        if (db != null) {
            try { db.rollback(); }
            finally { db.close(); }
        }
    }

    @Test
    void authorizedActivateRetainsVersionZeroAndDoesNotWriteHistory() throws Exception {
        Reply result = activate("alice", true, "instance-a", 0, 11);
        assertThat(result.outcome()).isEqualTo("SNAPSHOT");
        assertThat(result.body()).contains("ACTIVE", "instance-a");
        assertThat(number("SELECT runtime_version FROM " + schema + ".workflow_instances")).isZero();
        assertThat(text("SELECT lifecycle FROM " + schema + ".workflow_instances")).isEqualTo("ACTIVE");
        assertThat(number("SELECT count(*) FROM " + schema + ".workflow_transition_history")).isZero();
    }

    @Test
    void authorizedTransitionsAdvanceOneVersionAndAppendMatchingHistory() throws Exception {
        assertThat(activate("alice", true, "instance-a", 0, 11).outcome()).isEqualTo("SNAPSHOT");
        assertThat(transition("alice", true, "instance-a", 0, "assign", 12).outcome()).isEqualTo("SNAPSHOT");
        assertThat(number("SELECT runtime_version FROM " + schema + ".workflow_instances")).isEqualTo(1);
        assertThat(text("SELECT current_state FROM " + schema + ".workflow_instances")).isEqualTo("assigned");
        assertThat(text("SELECT lifecycle FROM " + schema + ".workflow_instances")).isEqualTo("ACTIVE");
        assertThat(number("SELECT count(*) FROM " + schema + ".workflow_transition_history")).isEqualTo(1);

        Reply last = transition("alice", true, "instance-a", 1, "resolve", 13);
        assertThat(last.outcome()).isEqualTo("SNAPSHOT");
        assertThat(last.body()).contains("done", "alice", "resolve");
        assertThat(text("SELECT lifecycle FROM " + schema + ".workflow_instances")).isEqualTo("COMPLETED");
        assertThat(number("SELECT runtime_version FROM " + schema + ".workflow_instances")).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM " + schema + ".workflow_transition_history")).isEqualTo(2);
        assertThat(text("SELECT actor_id FROM " + schema
                + ".workflow_transition_history WHERE resulting_runtime_version=2")).isEqualTo("alice");
    }

    @Test
    void missingActorAndCrossTenantActorReceiveHiddenWithoutMutation() throws Exception {
        assertThat(activate("alice", true, "missing-id", 0, 11).outcome()).isEqualTo("HIDDEN");
        assertThat(activate("bob", true, "instance-a", 0, 11).outcome()).isEqualTo("HIDDEN");
        assertThat(transition("bob", true, "instance-a", 0, "assign", 11).outcome()).isEqualTo("HIDDEN");
        assertNotStarted();
    }

    @Test
    void deniedAuthorityOrRevokedExactGrantLeavesSnapshotUnchanged() throws Exception {
        assertThat(activate("alice", false, "instance-a", 0, 11).outcome()).isEqualTo("FORBIDDEN");
        sql("UPDATE " + schema + ".resource_acl SET granted=false"
                + " WHERE operation='workflow:activate'");
        assertThat(activate("alice", true, "instance-a", 0, 11).outcome()).isEqualTo("FORBIDDEN");
        assertNotStarted();

        // Transition is independently authorized; activate fixture manually for this negative case.
        sql("UPDATE " + schema + ".workflow_instances SET lifecycle='ACTIVE'");
        assertThat(transition("alice", false, "instance-a", 0, "assign", 12).outcome()).isEqualTo("FORBIDDEN");
        sql("UPDATE " + schema + ".resource_acl SET granted=false"
                + " WHERE operation='workflow:transition'");
        assertThat(transition("alice", true, "instance-a", 0, "assign", 12).outcome()).isEqualTo("FORBIDDEN");
        assertThat(number("SELECT count(*) FROM " + schema + ".workflow_transition_history")).isZero();
        assertThat(number("SELECT runtime_version FROM " + schema + ".workflow_instances")).isZero();
    }

    @Test
    void staleExpectedVersionDoesNotDuplicateHistoryOrRewriteSnapshot() throws Exception {
        assertThat(activate("alice", true, "instance-a", 0, 11).outcome()).isEqualTo("SNAPSHOT");
        assertThat(transition("alice", true, "instance-a", 0, "assign", 12).outcome()).isEqualTo("SNAPSHOT");
        assertThat(transition("alice", true, "instance-a", 0, "assign", 13).outcome()).isEqualTo("CONFLICT");
        // A stale proposal conflicts even when the instance is already ACTIVE.
        assertThat(activate("alice", true, "instance-a", 0, 13).outcome()).isEqualTo("CONFLICT");
        // With the correct current version, a second activation is rejected by lifecycle.
        assertThat(activate("alice", true, "instance-a", 1, 13).outcome()).isEqualTo("REJECTED");
        assertThat(number("SELECT count(*) FROM " + schema + ".workflow_transition_history")).isEqualTo(1);
        assertThat(number("SELECT runtime_version FROM " + schema + ".workflow_instances")).isEqualTo(1);
    }

    @Test
    void invalidTransitionAndBackwardsTimestampNeverWrite() throws Exception {
        assertThat(activate("alice", true, "instance-a", 0, 11).outcome()).isEqualTo("SNAPSHOT");
        assertThat(transition("alice", true, "instance-a", 0, "unknown", 12).outcome())
                .isEqualTo("INVALID_TRANSITION");
        assertThat(transition("alice", true, "instance-a", 0, "assign", 9).outcome())
                .isEqualTo("REJECTED");
        assertThat(number("SELECT count(*) FROM " + schema + ".workflow_transition_history")).isZero();
        assertThat(text("SELECT current_state FROM " + schema + ".workflow_instances")).isEqualTo("new");
        assertThat(number("SELECT runtime_version FROM " + schema + ".workflow_instances")).isZero();
    }

    private void assertNotStarted() throws Exception {
        assertThat(text("SELECT lifecycle FROM " + schema + ".workflow_instances"))
                .isEqualTo("NOT_STARTED");
        assertThat(number("SELECT runtime_version FROM " + schema + ".workflow_instances")).isZero();
        assertThat(number("SELECT count(*) FROM " + schema + ".workflow_transition_history")).isZero();
    }

    private Reply activate(String actor, boolean authority, String id, long version, long seconds)
            throws Exception {
        try (PreparedStatement statement = db.prepareStatement(
                "SELECT outcome, body FROM " + schema + ".wf_port_commit_activation(?,?,?,?,?,?)")) {
            statement.setString(1, actor);
            statement.setBoolean(2, authority);
            statement.setString(3, id);
            statement.setLong(4, version);
            statement.setLong(5, seconds);
            statement.setInt(6, 0);
            return reply(statement);
        }
    }

    private Reply transition(String actor, boolean authority, String id, long version,
                             String transitionId, long seconds) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(
                "SELECT outcome, body FROM " + schema + ".wf_port_commit_transition(?,?,?,?,?,?,?)")) {
            statement.setString(1, actor);
            statement.setBoolean(2, authority);
            statement.setString(3, id);
            statement.setLong(4, version);
            statement.setString(5, transitionId);
            statement.setLong(6, seconds);
            statement.setInt(7, 0);
            return reply(statement);
        }
    }

    private static Reply reply(PreparedStatement statement) throws Exception {
        try (ResultSet rows = statement.executeQuery()) {
            assertThat(rows.next()).isTrue();
            Reply result = new Reply(rows.getString(1), rows.getString(2));
            assertThat(rows.next()).isFalse();
            return result;
        }
    }

    private int number(String query) throws Exception {
        try (Statement statement = db.createStatement();
             ResultSet rows = statement.executeQuery(query)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    private String text(String query) throws Exception {
        try (Statement statement = db.createStatement();
             ResultSet rows = statement.executeQuery(query)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private void sql(String query) throws Exception {
        try (Statement statement = db.createStatement()) {
            statement.execute(query);
        }
    }

    private record Reply(String outcome, String body) {}
}
