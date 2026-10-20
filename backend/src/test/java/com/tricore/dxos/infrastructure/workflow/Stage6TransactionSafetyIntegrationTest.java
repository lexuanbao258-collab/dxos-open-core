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
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stage 6.4 disposable-PG17, two-connection transaction candidate tests.
 * The unique test schema is committed ONLY to make it visible to both sessions,
 * then dropped after each test. No existing schema, staging, Identity or HTTP is touched.
 * Does NOT approve the unimplemented production guarded JDBC or policy-admin API.
 */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/stage6_disposable_[a-zA-Z0-9_]+")
class Stage6TransactionSafetyIntegrationTest {
    private Connection setup;
    private String schema;
    private String url;
    private String user;
    private String password;

    @BeforeEach
    void prepare() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")));
        url = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        URI uri = URI.create(url.substring(5));
        assumeTrue(("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
                && uri.getPath().matches("/stage6_disposable_[a-zA-Z0-9_]+"));
        user = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        password = System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD");
        assumeTrue(user != null && !user.isBlank() && password != null);
        setup = open();
        int pgVersion = queryInt(setup, "SELECT current_setting('server_version_num')::integer");
        assertThat(pgVersion).isBetween(170000, 179999);
        schema = "s6_tx_" + UUID.randomUUID().toString().replace("-", "");
        exec(setup, "CREATE SCHEMA " + schema);
        String placeholder = "$" + "{flyway:defaultSchema}";
        for (String file : new String[] {
                "src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                "src/test/resources/stage6/Stage6_guarded_create_candidate.sql"
        }) {
            exec(setup, Files.readString(Path.of(file)).replace(placeholder, schema));
        }
        exec(setup, "INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a')");
        exec(setup, "INSERT INTO " + schema
                + ".tenant_memberships(tenant_id,actor_id,active) VALUES('tenant-a','member-a',true)");
        exec(setup, "INSERT INTO " + schema
                + ".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle)"
                + " VALUES('item','res-a','tenant-a','member-a','ACTIVE')");
        exec(setup, "INSERT INTO " + schema
                + ".resource_acl(tenant_id,resource_type,resource_id,actor_id,operation,granted)"
                + " VALUES('tenant-a','item','res-a','member-a','workflow:create',true)");
        exec(setup, "INSERT INTO " + schema
                + ".workflow_definitions(definition_id,definition_version,initial_state)"
                + " VALUES('publication',7,'draft')");
        exec(setup, "INSERT INTO " + schema
                + ".workflow_definition_states(definition_id,definition_version,state_id)"
                + " VALUES('publication',7,'draft')");
        exec(setup, "INSERT INTO " + schema
                + ".workflow_definition_bindings(tenant_id,resource_type,definition_id,definition_version,active)"
                + " VALUES('tenant-a','item','publication',7,true)");
        exec(setup, "INSERT INTO " + schema
                + ".workflow_policy_audit(audit_event_id,tenant_id,previous_acl_revision,"
                + "resulting_acl_revision,actor_id,actor_source_ref,action,change_set)"
                + " VALUES('" + UUID.randomUUID() + "','tenant-a',NULL,0,"
                + "'operator','approved-disposable','tenant-bootstrap',"
                + "'[{\"tenant_id\":\"tenant-a\"}]'::jsonb)");
        setup.commit();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (setup == null) return;
        try {
            setup.rollback();
            if (schema != null && schema.matches("s6_tx_[a-f0-9]{32}")) {
                exec(setup, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                setup.commit();
            }
        } finally {
            setup.close();
        }
    }

    @Test
    void revokeLocksFirstAndWaitingCreateIsDeniedWithoutWrite() throws Exception {
        try (Connection revoker = open()) {
            lockGuard(revoker);
            exec(revoker, "UPDATE " + schema + ".resource_acl SET granted=false");
            var executor = Executors.newSingleThreadExecutor();
            try {
                AtomicInteger waiterPid = new AtomicInteger();
                CountDownLatch ready = new CountDownLatch(1);
                Future<String> waiting = executor.submit(() -> {
                    try (Connection creator = open()) {
                        waiterPid.set(queryInt(creator, "SELECT pg_backend_pid()"));
                        ready.countDown();
                        exec(creator, "SET LOCAL statement_timeout='8000ms'");
                        String outcome = create(creator, newId());
                        creator.commit();
                        return outcome;
                    }
                });
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(waitUntilBlocked(waiterPid.get())).isTrue();
                finishRevocation(revoker);
                revoker.commit();
                assertThat(waiting.get(8, TimeUnit.SECONDS)).isEqualTo("FORBIDDEN");
            } finally {
                revoker.rollback(); // always release guard before shutting down waiter
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
        try (Connection observer = open()) {
            assertThat(queryInt(observer, "SELECT count(*) FROM " + schema + ".workflow_instances")).isZero();
            assertThat(queryInt(observer, "SELECT acl_revision FROM " + schema
                    + ".tenant_guards WHERE tenant_id='tenant-a'")).isEqualTo(1);
            assertThat(queryInt(observer, "SELECT count(*) FROM " + schema
                    + ".workflow_policy_audit")).isEqualTo(2);
        }
    }

    @Test
    void createLocksFirstAndLaterRevokeWaitsForConfirmedCommit() throws Exception {
        try (Connection creator = open()) {
            assertThat(create(creator, newId())).isEqualTo("SNAPSHOT");
            var executor = Executors.newSingleThreadExecutor();
            try {
                AtomicInteger waiterPid = new AtomicInteger();
                CountDownLatch ready = new CountDownLatch(1);
                Future<?> waiting = executor.submit(() -> {
                    try (Connection revoker = open()) {
                        waiterPid.set(queryInt(revoker, "SELECT pg_backend_pid()"));
                        ready.countDown();
                        exec(revoker, "SET LOCAL statement_timeout='8000ms'");
                        lockGuard(revoker);
                        exec(revoker, "UPDATE " + schema + ".resource_acl SET granted=false");
                        finishRevocation(revoker);
                        revoker.commit();
                    }
                    return null;
                });
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(waitUntilBlocked(waiterPid.get())).isTrue();
                creator.commit();
                waiting.get(8, TimeUnit.SECONDS);
            } finally {
                creator.rollback();
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
        try (Connection observer = open()) {
            assertThat(queryInt(observer, "SELECT count(*) FROM " + schema + ".workflow_instances"))
                    .isEqualTo(1);
            assertThat(create(observer, newId())).isEqualTo("FORBIDDEN");
            observer.rollback();
        }
    }

    @Test
    void rejectedAuditMustRollbackAclAndRevisionTogether() throws Exception {
        try (Connection revoker = open()) {
            lockGuard(revoker);
            exec(revoker, "UPDATE " + schema + ".resource_acl SET granted=false");
            exec(revoker, "UPDATE " + schema + ".tenant_guards SET acl_revision=1");
            try {
                exec(revoker, "INSERT INTO " + schema
                        + ".workflow_policy_audit(audit_event_id,tenant_id,previous_acl_revision,"
                        + "resulting_acl_revision,actor_id,actor_source_ref,action,change_set)"
                        + " VALUES('" + UUID.randomUUID() + "','tenant-a',0,1,"
                        + "'operator','approved-disposable','acl-revoke','[]'::jsonb)");
                throw new AssertionError("Empty audit change_set must fail");
            } catch (SQLException e) {
                assertThat(e.getSQLState()).isEqualTo("23514");
            }
            revoker.rollback();
        }
        try (Connection observer = open()) {
            assertThat(queryInt(observer, "SELECT count(*) FROM " + schema
                    + ".resource_acl WHERE granted")).isEqualTo(1);
            assertThat(queryInt(observer, "SELECT acl_revision FROM " + schema
                    + ".tenant_guards WHERE tenant_id='tenant-a'")).isZero();
            assertThat(queryInt(observer, "SELECT count(*) FROM " + schema
                    + ".workflow_policy_audit")).isEqualTo(1);
            observer.rollback();
        }
    }

    private void lockGuard(Connection c) throws SQLException {
        exec(c, "SELECT tenant_id FROM " + schema
                + ".tenant_guards WHERE tenant_id='tenant-a' FOR UPDATE");
    }

    private void finishRevocation(Connection c) throws SQLException {
        exec(c, "UPDATE " + schema
                + ".tenant_guards SET acl_revision=1 WHERE tenant_id='tenant-a' AND acl_revision=0");
        exec(c, "INSERT INTO " + schema
                + ".workflow_policy_audit(audit_event_id,tenant_id,previous_acl_revision,"
                + "resulting_acl_revision,actor_id,actor_source_ref,action,change_set)"
                + " VALUES('" + UUID.randomUUID() + "','tenant-a',0,1,"
                + "'operator','approved-disposable','acl-revoke',"
                + "'[{\"operation\":\"workflow:create\",\"granted\":false}]'::jsonb)");
    }

    private String create(Connection c, String id) throws SQLException {
        String json = """
                {"instance_id":"%s","definition_id":"publication","definition_version":7,
                 "resource_type":"item","resource_id":"res-a","current_state":"draft",
                 "runtime_version":0,"lifecycle":"NOT_STARTED","created_at_seconds":0,
                 "created_at_nanos":0,"updated_at_seconds":0,"updated_at_nanos":0}
                """.formatted(id);
        try (PreparedStatement p = c.prepareStatement(
                "SELECT outcome FROM " + schema + ".wf_port_create_instance(?,?,?::jsonb)")) {
            p.setString(1, "member-a");
            p.setBoolean(2, true);
            p.setString(3, json);
            try (var rows = p.executeQuery()) {
                assertThat(rows.next()).isTrue();
                String outcome = rows.getString(1);
                assertThat(rows.next()).isFalse();
                return outcome;
            }
        }
    }

    private boolean waitUntilBlocked(int workerPid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            try (Connection inspector = open();
                 PreparedStatement p = inspector.prepareStatement(
                         "SELECT cardinality(pg_catalog.pg_blocking_pids(?))")) {
                p.setInt(1, workerPid);
                try (var row = p.executeQuery()) {
                    if (row.next() && row.getInt(1) > 0) return true;
                }
            }
            Thread.sleep(40);
        }
        return false;
    }

    private int queryInt(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var rows = s.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    private void exec(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement()) { s.execute(sql); }
    }

    private Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url, user, password);
        c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        c.setAutoCommit(false);
        return c;
    }

    private String newId() {
        return "wf_" + UUID.randomUUID().toString().replace("-", "");
    }
}
