package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Isolated PG17 candidate: real two-session tenant-guard coordination for
 * ACTIVATE and TRANSITION. Not production role, policy-admin or HTTP approval.
 * The per-test schema is committed for concurrency and dropped in @AfterEach.
 */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):55427/stage6_disposable_test")
class Stage6GuardedActionsRaceIntegrationTest {
    private Connection setup;
    private String schema;
    private String url;
    private String user;
    private String password;

    @BeforeEach
    void prepare() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")));
        assumeTrue("true".equals(System.getenv("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE")));
        url = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        URI endpoint = URI.create(url.substring(5));
        assumeTrue(("localhost".equals(endpoint.getHost()) || "127.0.0.1".equals(endpoint.getHost()))
                && endpoint.getPort() == 55427 && "/stage6_disposable_test".equals(endpoint.getPath()));
        user = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        password = System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD");
        assumeTrue("stage6_test".equals(user) && password != null);

        setup = open();
        assertThat(number(setup, "SELECT current_setting('server_version_num')::integer"))
                .isBetween(170000, 179999);
        assertThat(text(setup, "SELECT current_database()")).isEqualTo("stage6_disposable_test");

        schema = "s6_actions_race_" + UUID.randomUUID().toString().replace("-", "");
        sql(setup, "CREATE SCHEMA " + schema);
        for (String file : new String[] {
                "src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_actions_candidate.sql"
        }) {
            sql(setup, Files.readString(Path.of(file))
                    .replace("$" + "{flyway:defaultSchema}", schema));
        }
        sql(setup, "INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a')");
        sql(setup, "INSERT INTO " + schema
                + ".tenant_memberships(tenant_id,actor_id,active) VALUES('tenant-a','alice',true)");
        sql(setup, "INSERT INTO " + schema
                + ".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle)"
                + " VALUES('item','res-a','tenant-a','alice','ACTIVE')");
        sql(setup, "INSERT INTO " + schema
                + ".resource_acl(tenant_id,resource_type,resource_id,actor_id,operation,granted)"
                + " VALUES('tenant-a','item','res-a','alice','workflow:activate',true),"
                + "('tenant-a','item','res-a','alice','workflow:transition',true)");
        sql(setup, "INSERT INTO " + schema
                + ".workflow_policy_audit(audit_event_id,tenant_id,previous_acl_revision,"
                + "resulting_acl_revision,actor_id,actor_source_ref,action,change_set)"
                + " VALUES('" + UUID.randomUUID() + "','tenant-a',NULL,0,"
                + "'operator','disposable-race-test','tenant-bootstrap','[{\"fixture\":true}]'::jsonb)");
        sql(setup, "INSERT INTO " + schema
                + ".workflow_definitions(definition_id,definition_version,initial_state)"
                + " VALUES('ticket',1,'new')");
        sql(setup, "INSERT INTO " + schema
                + ".workflow_definition_states(definition_id,definition_version,state_id)"
                + " VALUES('ticket',1,'new'),('ticket',1,'assigned')");
        sql(setup, "INSERT INTO " + schema
                + ".workflow_definition_transitions"
                + " (definition_id,definition_version,transition_id,source_state,target_state)"
                + " VALUES('ticket',1,'assign','new','assigned')");
        sql(setup, "INSERT INTO " + schema
                + ".workflow_instances"
                + " (instance_id,definition_id,definition_version,resource_type,resource_id,"
                + "current_state,runtime_version,lifecycle,created_at_seconds,created_at_nanos,"
                + "updated_at_seconds,updated_at_nanos)"
                + " VALUES('instance-a','ticket',1,'item','res-a','new',0,'NOT_STARTED',10,0,10,0)");
        setup.commit();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (setup == null) return;
        try {
            setup.rollback();
            if (schema != null && schema.matches("s6_actions_race_[a-f0-9]{32}")) {
                sql(setup, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                setup.commit();
            }
        } finally {
            setup.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"activate", "transition"})
    void revocationLocksFirstSoWaitingActionIsDeniedWithoutMutation(String operation) throws Exception {
        prepareOperation(operation);
        try (Connection revoker = open()) {
            lockGuard(revoker);
            revoke(revoker, operation);
            var executor = Executors.newSingleThreadExecutor();
            try {
                AtomicInteger workerPid = new AtomicInteger();
                CountDownLatch ready = new CountDownLatch(1);
                Future<String> waiting = executor.submit(() -> {
                    try (Connection worker = open()) {
                        workerPid.set(number(worker, "SELECT pg_backend_pid()"));
                        ready.countDown();
                        sql(worker, "SET LOCAL statement_timeout='8000ms'");
                        String result = action(worker, operation);
                        worker.commit();
                        return result;
                    }
                });
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(waitUntilBlocked(workerPid.get())).isTrue();
                recordRevocation(revoker, operation);
                revoker.commit();
                assertThat(waiting.get(8, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");
            } finally {
                revoker.rollback(); // release locks even when an assertion fails
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
        try (Connection observer = open()) {
            assertThat(text(observer, "SELECT lifecycle FROM " + schema + ".workflow_instances"))
                    .isEqualTo(operation.equals("activate") ? "NOT_STARTED" : "ACTIVE");
            assertThat(number(observer, "SELECT runtime_version FROM " + schema
                    + ".workflow_instances")).isZero();
            assertThat(number(observer, "SELECT count(*) FROM " + schema
                    + ".workflow_transition_history")).isZero();
            assertThat(number(observer, "SELECT acl_revision FROM " + schema
                    + ".tenant_guards WHERE tenant_id='tenant-a'")).isEqualTo(1);
            assertThat(number(observer, "SELECT count(*) FROM " + schema
                    + ".workflow_policy_audit")).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"activate", "transition"})
    void guardedActionLocksFirstAndRevocationWaitsUntilItsCommit(String operation) throws Exception {
        prepareOperation(operation);
        try (Connection writer = open()) {
            assertThat(action(writer, operation)).isEqualTo("SNAPSHOT");
            var executor = Executors.newSingleThreadExecutor();
            try {
                AtomicInteger revokerPid = new AtomicInteger();
                CountDownLatch ready = new CountDownLatch(1);
                Future<?> waiting = executor.submit(() -> {
                    try (Connection revoker = open()) {
                        revokerPid.set(number(revoker, "SELECT pg_backend_pid()"));
                        ready.countDown();
                        sql(revoker, "SET LOCAL statement_timeout='8000ms'");
                        lockGuard(revoker);
                        revoke(revoker, operation);
                        recordRevocation(revoker, operation);
                        revoker.commit();
                    }
                    return null;
                });
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(waitUntilBlocked(revokerPid.get())).isTrue();
                writer.commit();
                waiting.get(8, TimeUnit.SECONDS);
            } finally {
                writer.rollback();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
        try (Connection observer = open()) {
            assertThat(number(observer, "SELECT count(*) FROM " + schema
                    + ".resource_acl WHERE operation='workflow:" + operation
                    + "' AND granted")).isZero();
            assertThat(text(observer, "SELECT lifecycle FROM " + schema + ".workflow_instances"))
                    .isEqualTo(operation.equals("activate") ? "ACTIVE" : "COMPLETED");
            assertThat(number(observer, "SELECT runtime_version FROM " + schema
                    + ".workflow_instances")).isEqualTo(operation.equals("activate") ? 0 : 1);
            assertThat(number(observer, "SELECT count(*) FROM " + schema
                    + ".workflow_transition_history")).isEqualTo(operation.equals("activate") ? 0 : 1);
        }
    }

    private void prepareOperation(String operation) throws Exception {
        if (operation.equals("transition")) {
            sql(setup, "UPDATE " + schema + ".workflow_instances SET lifecycle='ACTIVE'");
            setup.commit();
        }
    }

    private String action(Connection c, String operation) throws Exception {
        String name = operation.equals("activate")
                ? "wf_port_commit_activation(?,?,?,?,?,?)"
                : "wf_port_commit_transition(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = c.prepareStatement(
                "SELECT outcome FROM " + schema + "." + name)) {
            statement.setString(1, "alice");
            statement.setBoolean(2, true);
            statement.setString(3, "instance-a");
            statement.setLong(4, 0);
            int whenParameter;
            if (operation.equals("transition")) {
                statement.setString(5, "assign");
                whenParameter = 6;
            } else {
                whenParameter = 5;
            }
            statement.setLong(whenParameter, 11);
            statement.setInt(whenParameter + 1, 0);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                String result = rows.getString(1);
                assertThat(rows.next()).isFalse();
                return result;
            }
        }
    }

    private void lockGuard(Connection c) throws Exception {
        sql(c, "SELECT tenant_id FROM " + schema
                + ".tenant_guards WHERE tenant_id='tenant-a' FOR UPDATE");
    }

    private void revoke(Connection c, String operation) throws Exception {
        sql(c, "UPDATE " + schema
                + ".resource_acl SET granted=false WHERE operation='workflow:" + operation + "'");
    }

    private void recordRevocation(Connection c, String operation) throws Exception {
        sql(c, "UPDATE " + schema
                + ".tenant_guards SET acl_revision=1 WHERE tenant_id='tenant-a' AND acl_revision=0");
        sql(c, "INSERT INTO " + schema
                + ".workflow_policy_audit(audit_event_id,tenant_id,previous_acl_revision,"
                + "resulting_acl_revision,actor_id,actor_source_ref,action,change_set)"
                + " VALUES('" + UUID.randomUUID() + "','tenant-a',0,1,"
                + "'operator','disposable-race-test','acl-revoke',"
                + "'[{\"operation\":\"workflow:" + operation + "\",\"granted\":false}]'::jsonb)");
    }

    private boolean waitUntilBlocked(int pid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            try (Connection inspector = open();
                 PreparedStatement statement = inspector.prepareStatement(
                         "SELECT cardinality(pg_catalog.pg_blocking_pids(?))")) {
                statement.setInt(1, pid);
                try (ResultSet row = statement.executeQuery()) {
                    if (row.next() && row.getInt(1) > 0) return true;
                }
            }
            Thread.sleep(40);
        }
        return false;
    }

    private static String text(Connection c, String statementText) throws SQLException {
        try (Statement statement = c.createStatement();
             ResultSet row = statement.executeQuery(statementText)) {
            assertThat(row.next()).isTrue();
            return row.getString(1);
        }
    }

    private static int number(Connection c, String statementText) throws SQLException {
        try (Statement statement = c.createStatement();
             ResultSet row = statement.executeQuery(statementText)) {
            assertThat(row.next()).isTrue();
            return row.getInt(1);
        }
    }

    private static void sql(Connection c, String statementText) throws SQLException {
        try (Statement statement = c.createStatement()) {
            statement.execute(statementText);
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
        return connection;
    }
}
