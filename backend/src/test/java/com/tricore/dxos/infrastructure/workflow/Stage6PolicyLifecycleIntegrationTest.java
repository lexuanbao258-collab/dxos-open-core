package com.tricore.dxos.infrastructure.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tricore.dxos.application.authorization.WorkflowAccessException;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Test-only owner-session policy prototype, not production OIDC/admin or runtime SQL privilege approval. */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):55427/stage6_disposable_test")
class Stage6PolicyLifecycleIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private Connection setup;
    private String schema;
    private String url;
    private String user;
    private String password;
    private DriverManagerDataSource database;
    private final UUID bootstrap = UUID.randomUUID();

    @BeforeEach
    void prepare() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")));
        assumeTrue("true".equals(System.getenv("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE")));
        url = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        URI endpoint = URI.create(url.substring(5));
        assumeTrue(Set.of("localhost", "127.0.0.1").contains(endpoint.getHost())
                && endpoint.getPort() == 55427 && "/stage6_disposable_test".equals(endpoint.getPath()));
        user = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        password = System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD");
        assumeTrue("stage6_test".equals(user) && password != null);
        setup = open();
        assertThat(number(setup, "SHOW server_version_num")).isBetween(170000, 179999);
        assertThat(text(setup, "SELECT current_database()")).isEqualTo("stage6_disposable_test");
        schema = "s6_policy_" + UUID.randomUUID().toString().replace("-", "");
        sql(setup, "CREATE SCHEMA " + schema);
        for (String file : List.of("src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql",
                "src/test/resources/stage6/Stage6_policy_lifecycle_candidate.sql")) {
            sql(setup, Files.readString(Path.of(file)).replace("${flyway:defaultSchema}", schema));
        }
        sql(setup, "INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a'),('tenant-b')");
        sql(setup, "INSERT INTO " + schema + ".tenant_memberships VALUES"
                + "('tenant-a','alice',true),('tenant-a','bob',true),('tenant-a','inactive',false),"
                + "('tenant-b','alice',true),('tenant-b','foreign',true)");
        sql(setup, "INSERT INTO " + schema
                + ".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle,tombstoned_at) VALUES"
                + "('item','r1','tenant-a','alice','ACTIVE',NULL),('item','r2','tenant-a','alice','ACTIVE',NULL),"
                + "('item','old','tenant-a','alice','TOMBSTONED',now()),('item','foreign','tenant-b','alice','ACTIVE',NULL)");
        sql(setup, "INSERT INTO " + schema + ".resource_acl VALUES"
                + "('tenant-a','item','r1','alice','workflow:read',true),"
                + "('tenant-a','item','r1','alice','workflow:create',true),"
                + "('tenant-a','item','r2','alice','workflow:transition',true),"
                + "('tenant-a','item','old','alice','workflow:read',true),"
                + "('tenant-b','item','foreign','alice','workflow:read',true)");
        for (String tenant : List.of("tenant-a", "tenant-b")) {
            try (var statement = setup.prepareStatement("INSERT INTO " + schema
                    + ".workflow_policy_audit(audit_event_id,tenant_id,previous_acl_revision,resulting_acl_revision,"
                    + "actor_id,actor_source_ref,action,change_set,reason_or_approval_ref,correlation_ref)"
                    + " VALUES(?,?,NULL,0,current_user,'disposable-owner-session','tenant-bootstrap',"
                    + "'[{\"fixture\":\"synthetic-only\"}]','approved-disposable','bootstrap')")) {
                statement.setObject(1, tenant.equals("tenant-a") ? bootstrap : UUID.randomUUID());
                statement.setString(2, tenant);
                statement.executeUpdate();
            }
        }
        sql(setup, "INSERT INTO " + schema + ".workflow_definitions VALUES('publication',7,'draft')");
        sql(setup, "INSERT INTO " + schema + ".workflow_definition_states VALUES('publication',7,'draft')");
        sql(setup, "INSERT INTO " + schema + ".workflow_instances VALUES"
                + "('instance-a','publication',7,'item','r1','draft',0,'NOT_STARTED',0,123,0,123)");
        setup.commit();
        database = new DriverManagerDataSource(url, user, password);
        Properties properties = new Properties();
        properties.setProperty("currentSchema", schema + ",pg_catalog,pg_temp");
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "20");
        database.setConnectionProperties(properties);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (setup == null) return;
        try {
            setup.rollback();
            if (schema != null && schema.matches("s6_policy_[a-f0-9]{32}")) {
                sql(setup, "DROP SCHEMA " + schema + " CASCADE");
                setup.commit();
            }
        } finally { setup.close(); }
    }

    @Test
    void deactivationReassignsAllActiveOwnersRevokesEveryGrantAndReactivationRestoresNeither() throws Exception {
        int writer;
        try (Connection connection = open()) {
            writer = number(connection, "SELECT pg_backend_pid()");
            assertThat(membership(connection, "alice", false, "bob", 0, UUID.randomUUID(), "approved-deactivation"))
                    .isEqualTo("CHANGED");
            connection.commit();
        }
        try (Connection observer = open()) {
            assertThat(number(observer, "SELECT pg_backend_pid()")).isNotEqualTo(writer);
            assertDeactivated(observer, 1);
            JsonNode event = audit(observer, 1);
            assertThat(event.get("actor_id").asText()).isEqualTo(user);
            assertThat(event.get("actor_source_ref").asText()).isEqualTo("disposable-owner-session");
            assertThat(event.get("action").asText()).isEqualTo("membership-deactivate");
            assertThat(event.get("reason_or_approval_ref").asText()).isEqualTo("approved-deactivation");
            assertThat(event.get("correlation_ref").asText()).startsWith("test/");
            assertThat(event.get("recorded_at").asText()).isNotBlank();
            JsonNode expected = JSON.readTree("""
                    [{"relation":"resource_registry","tenant_id":"tenant-a","resource_type":"item","resource_id":"r1",
                      "before":{"owner_actor_id":"alice"},"after":{"owner_actor_id":"bob"}},
                     {"relation":"resource_registry","tenant_id":"tenant-a","resource_type":"item","resource_id":"r2",
                      "before":{"owner_actor_id":"alice"},"after":{"owner_actor_id":"bob"}},
                     {"relation":"resource_acl","tenant_id":"tenant-a","resource_type":"item","resource_id":"r1",
                      "actor_id":"alice","operation":"workflow:create","before":{"granted":true},"after":{"granted":false}},
                     {"relation":"resource_acl","tenant_id":"tenant-a","resource_type":"item","resource_id":"r1",
                      "actor_id":"alice","operation":"workflow:read","before":{"granted":true},"after":{"granted":false}},
                     {"relation":"resource_acl","tenant_id":"tenant-a","resource_type":"item","resource_id":"r2",
                      "actor_id":"alice","operation":"workflow:transition","before":{"granted":true},"after":{"granted":false}},
                     {"relation":"resource_acl","tenant_id":"tenant-a","resource_type":"item","resource_id":"old",
                      "actor_id":"alice","operation":"workflow:read","before":{"granted":true},"after":{"granted":false}},
                     {"relation":"tenant_memberships","tenant_id":"tenant-a","actor_id":"alice",
                      "before":{"active":true},"after":{"active":false}}]
                    """);
            assertThat(event.get("change_set")).containsExactlyInAnyOrderElementsOf(expected);
        }
        try (Connection connection = open()) {
            membership(connection, "alice", true, null, 1, UUID.randomUUID(), "approved-reactivation");
            connection.commit();
        }
        try (Connection observer = open()) {
            assertThat(number(observer, "SELECT count(*) FROM " + schema + ".resource_acl WHERE tenant_id='tenant-a' AND granted"))
                    .isZero();
            assertThat(number(observer, "SELECT count(*) FROM " + schema + ".resource_registry WHERE lifecycle='ACTIVE'"
                    + " AND tenant_id='tenant-a' AND owner_actor_id='alice'")).isZero();
            assertThat(audit(observer, 2).get("change_set")).hasSize(1);
            assertThat(audit(observer, 2).get("action").asText()).isEqualTo("membership-reactivate");
            assertThat(audit(observer, 2).get("change_set").get(0).get("before").get("active").asBoolean()).isFalse();
            assertThat(audit(observer, 2).get("change_set").get(0).get("after").get("active").asBoolean()).isTrue();
        }
        try (Connection connection = open()) {
            expectSqlState("23514", () -> acl(connection, "old", "workflow:read", true, 2));
            connection.rollback();
        }
        try (Connection connection = open()) {
            assertThat(acl(connection, "r1", "workflow:read", true, 2)).isEqualTo("CHANGED");
            connection.commit();
        }
        try (Connection observer = open()) {
            assertRevision(observer, 3);
            assertThat(audit(observer, 3).get("change_set").get(0).get("before").get("granted").asBoolean()).isFalse();
            assertThat(audit(observer, 3).get("change_set").get(0).get("after").get("granted").asBoolean()).isTrue();
        }
        assertThat(readOutcome(database)).isEqualTo("ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "foreign", "inactive", "alice"})
    void invalidReplacementRejectsTheWholeMutation(String replacement) throws Exception {
        JsonNode before = snapshot();
        try (Connection connection = open()) {
            expectSqlState("23514", () -> membership(connection, "alice", false,
                    replacement.equals("missing") ? null : replacement, 0, UUID.randomUUID(), "replacement-denied"));
            connection.rollback();
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void confirmedNoOpsAndStaleRevisionDoNotFabricateAudit() throws Exception {
        JsonNode before = snapshot();
        try (Connection connection = open()) {
            assertThat(membership(connection, "alice", true, null, 0, UUID.randomUUID(), "already-active")).isEqualTo("NO_CHANGE");
            assertThat(acl(connection, "r1", "workflow:read", true, 0)).isEqualTo("NO_CHANGE");
            assertThat(acl(connection, "r2", "workflow:read", false, 0)).isEqualTo("NO_CHANGE");
            connection.commit();
        }
        assertThat(snapshot()).isEqualTo(before);
        try (Connection connection = open()) {
            expectSqlState("40001", () -> membership(connection, "alice", false, "bob", 9, UUID.randomUUID(), "stale"));
            connection.rollback();
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void auditInsertionFailureRollsBackMembershipAclOwnerAndRevision(boolean duplicateEvent) throws Exception {
        JsonNode before = snapshot();
        try (Connection connection = open()) {
            expectSqlState(duplicateEvent ? "23505" : "23514", () -> membership(connection, "alice", false, "bob", 0,
                    duplicateEvent ? bootstrap : UUID.randomUUID(), duplicateEvent ? "audit-collision" : ""));
            connection.rollback();
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void auditIsAppendOnlyAndUnprivilegedRoleCannotMutateOrInvokeCandidates() throws Exception {
        JsonNode before = snapshot();
        try (Connection connection = open()) {
            for (String command : List.of("UPDATE " + schema + ".workflow_policy_audit SET action='acl-revoke'",
                    "DELETE FROM " + schema + ".workflow_policy_audit", "TRUNCATE " + schema + ".workflow_policy_audit")) {
                var savepoint = connection.setSavepoint();
                expectSqlState("23514", () -> sql(connection, command));
                connection.rollback(savepoint);
            }
            // Existing built-in read-only role: no CREATE ROLE or GRANT, not actual runtime LOGIN approval.
            sql(connection, "SET LOCAL ROLE pg_read_all_data");
            for (String command : List.of(
                    "UPDATE " + schema + ".tenant_memberships SET active=false",
                    "UPDATE " + schema + ".resource_acl SET granted=true",
                    "UPDATE " + schema + ".resource_registry SET owner_actor_id='inactive'",
                    "UPDATE " + schema + ".tenant_guards SET acl_revision=acl_revision+1",
                    "DELETE FROM " + schema + ".workflow_policy_audit")) {
                var savepoint = connection.setSavepoint();
                expectSqlState("42501", () -> sql(connection, command));
                connection.rollback(savepoint);
            }
            var savepoint = connection.setSavepoint();
            expectSqlState("42501", () -> membership(connection, "alice", false, "bob", 0, UUID.randomUUID(), "forged"));
            connection.rollback(savepoint);
            expectSqlState("42501", () -> acl(connection, "r2", "workflow:read", true, 0));
            connection.rollback();
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"membership", "owner", "grant"})
    void deferredCrossRowChecksRejectInvalidFinalStates(String target) throws Exception {
        JsonNode before = snapshot();
        try (Connection connection = open()) {
            sql(connection, switch (target) {
                case "membership" -> "UPDATE " + schema + ".tenant_memberships SET active=false WHERE tenant_id='tenant-a' AND actor_id='alice'";
                case "owner" -> "UPDATE " + schema + ".resource_registry SET owner_actor_id='inactive' WHERE resource_id='r1'";
                default -> "INSERT INTO " + schema + ".resource_acl VALUES('tenant-a','item','r1','inactive','workflow:read',true)";
            });
            expectSqlState("23514", connection::commit);
            connection.rollback();
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deactivationAndGuardedReadSerializeInBothOrders(boolean readFirst) throws Exception {
        CountDownLatch commitReached = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2); Connection mutator = open()) {
            if (!readFirst) membership(mutator, "alice", false, "bob", 0, UUID.randomUUID(), "revoke-first");
            AtomicInteger readerPid = new AtomicInteger();
            CountDownLatch readerReady = new CountDownLatch(1);
            DataSource observed = observedRead(readerPid, readerReady,
                    readFirst ? commitReached : null, allowCommit);
            var reader = workers.submit(() -> readOutcome(observed));
            try {
                assertThat(readerReady.await(3, TimeUnit.SECONDS)).isTrue();
                if (readFirst) {
                    assertThat(commitReached.await(3, TimeUnit.SECONDS)).isTrue();
                    int mutatorPid = number(mutator, "SELECT pg_backend_pid()");
                    assertThat(mutatorPid).isNotEqualTo(readerPid.get());
                    var mutation = workers.submit(() -> {
                        membership(mutator, "alice", false, "bob", 0, UUID.randomUUID(), "read-first");
                        mutator.commit();
                        return "CHANGED";
                    });
                    awaitBlocked(mutatorPid);
                    allowCommit.countDown();
                    assertThat(reader.get(8, TimeUnit.SECONDS)).isEqualTo("ALLOWED");
                    assertThat(mutation.get(8, TimeUnit.SECONDS)).isEqualTo("CHANGED");
                } else {
                    awaitBlocked(readerPid.get());
                    mutator.commit();
                    assertThat(reader.get(8, TimeUnit.SECONDS)).isEqualTo("HIDDEN");
                }
            } finally {
                allowCommit.countDown();
                mutator.rollback();
            }
        }
        assertThat(readOutcome(database)).isEqualTo("HIDDEN");
        try (Connection observer = open()) { assertDeactivated(observer, 1); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deactivationAndFreshGrantSerializeWithoutRevivingAccess(boolean grantFirst) throws Exception {
        try (Connection first = open(); var workers = Executors.newSingleThreadExecutor()) {
            if (grantFirst) acl(first, "r2", "workflow:read", true, 0);
            else membership(first, "alice", false, "bob", 0, UUID.randomUUID(), "deactivate-first");
            AtomicInteger waiterPid = new AtomicInteger();
            CountDownLatch ready = new CountDownLatch(1);
            var second = workers.submit(() -> {
                try (Connection connection = open()) {
                    waiterPid.set(number(connection, "SELECT pg_backend_pid()"));
                    ready.countDown();
                    if (grantFirst) {
                        membership(connection, "alice", false, "bob", 1, UUID.randomUUID(), "grant-first");
                        connection.commit();
                        return "CHANGED";
                    }
                    expectSqlState("23514", () -> acl(connection, "r2", "workflow:read", true, 1));
                    connection.rollback();
                    return "DENIED";
                }
            });
            try {
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(waiterPid.get()).isNotEqualTo(number(first, "SELECT pg_backend_pid()"));
                awaitBlocked(waiterPid.get());
                first.commit();
                assertThat(second.get(8, TimeUnit.SECONDS)).isEqualTo(grantFirst ? "CHANGED" : "DENIED");
            } finally { first.rollback(); }
        }
        try (Connection observer = open()) { assertDeactivated(observer, grantFirst ? 2 : 1); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitRevokeAndGrantRaceProducesTwoAccurateAuditEvents(boolean grantFirst) throws Exception {
        String resource = grantFirst ? "r2" : "r1";
        try (Connection first = open(); var workers = Executors.newSingleThreadExecutor()) {
            assertThat(acl(first, resource, "workflow:read", grantFirst, 0)).isEqualTo("CHANGED");
            CountDownLatch ready = new CountDownLatch(1);
            AtomicInteger waiterPid = new AtomicInteger();
            var second = workers.submit(() -> {
                try (Connection connection = open()) {
                    waiterPid.set(number(connection, "SELECT pg_backend_pid()"));
                    ready.countDown();
                    String result = acl(connection, resource, "workflow:read", !grantFirst, 1);
                    connection.commit();
                    return result;
                }
            });
            try {
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(waiterPid.get()).isNotEqualTo(number(first, "SELECT pg_backend_pid()"));
                awaitBlocked(waiterPid.get());
                first.commit();
                assertThat(second.get(8, TimeUnit.SECONDS)).isEqualTo("CHANGED");
            } finally { first.rollback(); }
        }
        try (Connection observer = open()) {
            assertRevision(observer, 2);
            assertThat(audit(observer, 1).get("action").asText()).isEqualTo(grantFirst ? "acl-grant" : "acl-revoke");
            JsonNode change = audit(observer, 2).get("change_set").get(0);
            assertThat(change.get("resource_id").asText()).isEqualTo(resource);
            assertThat(change.get("actor_id").asText()).isEqualTo("alice");
            assertThat(change.get("before").get("granted").asBoolean()).isEqualTo(grantFirst);
            assertThat(change.get("after").get("granted").asBoolean()).isEqualTo(!grantFirst);
            assertThat(number(observer, "SELECT granted::int FROM " + schema + ".resource_acl"
                    + " WHERE tenant_id='tenant-a' AND resource_id='" + resource + "' AND actor_id='alice' AND operation='workflow:read'"))
                    .isEqualTo(grantFirst ? 0 : 1);
        }
    }

    @Test
    void uncertainCommitIsNotRetriedAndIsReconciledUsingAFreshConnection() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> transaction = new AtomicReference<>();
        UUID event = UUID.randomUUID();
        try (Connection real = open()) {
            Connection lostResponse = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("commit")) {
                            attempts.incrementAndGet();
                            transaction.set(text(real, "SELECT pg_current_xact_id()"));
                            real.commit();
                            throw new SQLException("simulated lost COMMIT acknowledgement", "08006");
                        }
                        try { return method.invoke(real, args); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
            assertThatThrownBy(() -> commitMembership(lostResponse, event))
                    .isInstanceOf(IllegalStateException.class).hasMessage("COMMIT_OUTCOME_UNKNOWN").hasNoCause();
        }
        assertThat(attempts.get()).isEqualTo(1);
        try (Connection observer = open()) {
            assertDeactivated(observer, 1);
            assertThat(audit(observer, 1).get("audit_event_id").asText()).isEqualTo(event.toString());
            assertThat(text(observer, "SELECT pg_xact_status('" + transaction.get() + "'::xid8)")).isEqualTo("committed");
        }
    }

    private void commitMembership(Connection connection, UUID event) {
        try { membership(connection, "alice", false, "bob", 0, event, "uncertain-commit"); }
        catch (SQLException failure) {
            try { connection.rollback(); }
            catch (SQLException rollbackFailure) { throw new IllegalStateException("COMMIT_OUTCOME_UNKNOWN"); }
            throw new IllegalStateException("UNAVAILABLE");
        }
        try { connection.commit(); }
        catch (SQLException failure) { throw new IllegalStateException("COMMIT_OUTCOME_UNKNOWN"); }
    }

    private String membership(Connection connection, String member, boolean active, String replacement,
                              long revision, UUID event, String reason) throws SQLException {
        return call(connection, "stage6_policy_membership_candidate(?,?,?,?,?,?,?,?)",
                "tenant-a", member, active, replacement, revision, event, reason, "test/" + event);
    }

    private String acl(Connection connection, String resource, String operation, boolean granted, long revision) throws SQLException {
        UUID event = UUID.randomUUID();
        return call(connection, "stage6_policy_acl_candidate(?,?,?,?,?,?,?,?,?,?)",
                "tenant-a", "item", resource, "alice", operation, granted, revision, event, "explicit-acl-action", "test/" + event);
    }

    private String call(Connection connection, String function, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT " + schema + "." + function)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private void assertDeactivated(Connection connection, int revision) throws Exception {
        assertThat(number(connection, "SELECT count(*) FROM " + schema + ".tenant_memberships"
                + " WHERE tenant_id='tenant-a' AND actor_id='alice' AND active")).isZero();
        assertThat(number(connection, "SELECT count(*) FROM " + schema + ".resource_acl"
                + " WHERE tenant_id='tenant-a' AND actor_id='alice' AND granted")).isZero();
        assertThat(number(connection, "SELECT count(*) FROM " + schema + ".resource_registry"
                + " WHERE tenant_id='tenant-a' AND lifecycle='ACTIVE' AND owner_actor_id='bob'")).isEqualTo(2);
        assertThat(text(connection, "SELECT owner_actor_id FROM " + schema + ".resource_registry WHERE resource_id='old'")).isEqualTo("alice");
        assertThat(number(connection, "SELECT count(*) FROM " + schema + ".resource_acl WHERE tenant_id='tenant-a' AND actor_id='bob'"))
                .isZero();
        assertThat(number(connection, "SELECT count(*) FROM " + schema + ".resource_acl WHERE tenant_id='tenant-b' AND granted")).isEqualTo(1);
        assertRevision(connection, revision);
    }

    private void assertRevision(Connection connection, int revision) throws Exception {
        assertThat(number(connection, "SELECT acl_revision FROM " + schema + ".tenant_guards WHERE tenant_id='tenant-a'"))
                .isEqualTo(revision);
        assertThat(number(connection, "SELECT count(*) FROM " + schema + ".workflow_policy_audit WHERE tenant_id='tenant-a'"))
                .isEqualTo(revision + 1);
        assertThat(number(connection, "SELECT count(*) FROM " + schema + ".workflow_policy_audit WHERE tenant_id='tenant-a'"
                + " AND resulting_acl_revision>0 AND resulting_acl_revision<>previous_acl_revision+1")).isZero();
    }

    private JsonNode audit(Connection connection, int revision) throws Exception {
        return JSON.readTree(text(connection, "SELECT to_jsonb(a)::text FROM " + schema
                + ".workflow_policy_audit a WHERE tenant_id='tenant-a' AND resulting_acl_revision=" + revision));
    }

    private JsonNode snapshot() throws Exception {
        try (Connection observer = open()) {
            return JSON.readTree(text(observer, "SELECT jsonb_build_object("
                    + "'guards',(SELECT jsonb_agg(to_jsonb(t) ORDER BY tenant_id) FROM " + schema + ".tenant_guards t),"
                    + "'members',(SELECT jsonb_agg(to_jsonb(t) ORDER BY tenant_id,actor_id) FROM " + schema + ".tenant_memberships t),"
                    + "'registry',(SELECT jsonb_agg(to_jsonb(t) ORDER BY resource_type,resource_id) FROM " + schema + ".resource_registry t),"
                    + "'acl',(SELECT jsonb_agg(to_jsonb(t) ORDER BY tenant_id,resource_type,resource_id,actor_id,operation) FROM " + schema + ".resource_acl t),"
                    + "'audit',(SELECT jsonb_agg(to_jsonb(t) ORDER BY tenant_id,resulting_acl_revision) FROM " + schema + ".workflow_policy_audit t))::text"));
        }
    }

    private String readOutcome(DataSource source) {
        CurrentPrincipal principal = mock(CurrentPrincipal.class);
        when(principal.requireVerified()).thenReturn(new AuthenticatedPrincipal("alice", "test", Set.of(new Authority("workflow:read")), true));
        try {
            new Stage6GuardedReadService(new PostgresWorkflowPersistence(source, schema), principal).loadInstance("instance-a");
            return "ALLOWED";
        } catch (WorkflowAccessException denied) { return denied.decision().name(); }
    }

    private DataSource observedRead(AtomicInteger pid, CountDownLatch ready, CountDownLatch commitReached,
                                    CountDownLatch allowCommit) throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(ignored -> {
            Connection connection = database.getConnection();
            pid.set(number(connection, "SELECT pg_backend_pid()"));
            ready.countDown();
            return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commit") && commitReached != null) {
                            commitReached.countDown();
                            if (!allowCommit.await(8, TimeUnit.SECONDS)) throw new SQLException("test barrier timeout");
                        }
                        try { return method.invoke(connection, args); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
        });
        return source;
    }

    private void awaitBlocked(int pid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            try (Connection observer = open()) {
                if (number(observer, "SELECT cardinality(pg_blocking_pids(" + pid + "))") > 0) return;
            }
            Thread.sleep(30);
        }
        fail("Expected PostgreSQL lock wait");
    }

    private Connection open() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "20");
        Connection connection = DriverManager.getConnection(url, properties);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
        sql(connection, "SET LOCAL lock_timeout='5s'");
        sql(connection, "SET LOCAL statement_timeout='10s'");
        return connection;
    }

    private static void expectSqlState(String state, SqlCall call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(SQLException.class,
                failure -> assertThat(failure.getSQLState()).isEqualTo(state));
    }

    @FunctionalInterface
    private interface SqlCall { void run() throws SQLException; }

    private static void sql(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static int number(Connection connection, String sql) throws SQLException {
        return Integer.parseInt(text(connection, sql));
    }

    private static String text(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
