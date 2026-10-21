package com.tricore.dxos.infrastructure.workflow;

import com.tricore.dxos.application.authorization.WorkflowAccessDecision;
import com.tricore.dxos.application.authorization.WorkflowAccessException;
import com.tricore.dxos.application.identity.CurrentPrincipal;
import com.tricore.dxos.core.identity.AuthenticatedPrincipal;
import com.tricore.dxos.core.identity.Authority;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
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

/** Direct-JDBC READ candidate only; commits unique disposable fixtures, no roles/Flyway/HTTP. */
@EnabledIfEnvironmentVariable(named = "STAGE6_DISPOSABLE_PG17_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/stage6_disposable_[a-zA-Z0-9_]+")
class Stage6GuardedReadIntegrationTest {
    private Connection administrator;
    private String schema;
    private String url;
    private String user;
    private String password;
    private DriverManagerDataSource database;

    @BeforeEach
    void prepare() throws Exception {
        assumeTrue("YES".equals(System.getenv("STAGE6_DISPOSABLE_PG17_APPROVAL")));
        assumeTrue("true".equals(System.getenv("WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE")));
        url = System.getenv("STAGE6_DISPOSABLE_PG17_URL");
        URI endpoint = URI.create(url.substring(5));
        assumeTrue(Set.of("localhost", "127.0.0.1").contains(endpoint.getHost())
                && endpoint.getPort() == 55427
                && "/stage6_disposable_test".equals(endpoint.getPath()), "Only approved disposable PG17 on 55427");
        user = System.getenv("STAGE6_DISPOSABLE_PG17_USER");
        password = System.getenv("STAGE6_DISPOSABLE_PG17_PASSWORD");
        assumeTrue(user != null && !user.isBlank() && password != null);
        administrator = open();
        assertThat(number(administrator, "SHOW server_version_num")).isBetween(170000, 179999);
        assertThat(text(administrator, "SELECT current_database()")).isEqualTo("stage6_disposable_test");
        schema = "s6_read_" + UUID.randomUUID().toString().replace("-", "");
        sql(administrator, "CREATE SCHEMA " + schema);
        for (String file : List.of("src/main/resources/db/migration/V3__create_workflow_persistence.sql",
                "src/test/resources/stage6/V5_acl_physical_candidate.sql")) {
            sql(administrator, Files.readString(Path.of(file)).replace("${flyway:defaultSchema}", schema));
        }
        sql(administrator, "INSERT INTO " + schema + ".tenant_guards(tenant_id) VALUES('tenant-a'),('tenant-b')");
        sql(administrator, "INSERT INTO " + schema + ".tenant_memberships VALUES"
                + "('tenant-a','actor-a',true),('tenant-a','member',true),('tenant-b','actor-b',true)");
        sql(administrator, "INSERT INTO " + schema
                + ".resource_registry(resource_type,resource_id,tenant_id,owner_actor_id,lifecycle) VALUES"
                + "('item','res-a','tenant-a','actor-a','ACTIVE'),('item','res-b','tenant-b','actor-b','ACTIVE')");
        sql(administrator, "INSERT INTO " + schema + ".resource_acl VALUES"
                + "('tenant-a','item','res-a','actor-a','workflow:read',true),"
                + "('tenant-b','item','res-b','actor-b','workflow:read',true)");
        sql(administrator, "INSERT INTO " + schema + ".workflow_definitions(definition_id,definition_version,initial_state)"
                + " VALUES('publication',7,'draft')");
        sql(administrator, "INSERT INTO " + schema + ".workflow_definition_states VALUES"
                + "('publication',7,'draft'),('publication',7,'done')");
        sql(administrator, "INSERT INTO " + schema + ".workflow_definition_transitions VALUES"
                + "('publication',7,'finish','draft','done')");
        for (String side : List.of("a", "b")) {
            sql(administrator, "INSERT INTO " + schema + ".workflow_instances VALUES"
                    + "('instance-" + side + "','publication',7,'item','res-" + side
                    + "','done',1,'COMPLETED',0,123,1,456)");
            sql(administrator, "INSERT INTO " + schema + ".workflow_transition_history VALUES"
                    + "('instance-" + side + "',1,'finish','draft','done','actor-" + side + "',1,456)");
        }
        sql(administrator, "INSERT INTO " + schema + ".workflow_instances VALUES"
                + "('empty-a','publication',7,'item','res-a','draft',0,'NOT_STARTED',0,123,0,123)");
        administrator.commit();
        database = new DriverManagerDataSource(url, user, password);
        Properties properties = new Properties();
        properties.setProperty("currentSchema", schema + ",pg_catalog,pg_temp");
        database.setConnectionProperties(properties);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (administrator == null) return;
        try {
            administrator.rollback();
            if (schema != null && schema.matches("s6_read_[a-f0-9]{32}")) {
                sql(administrator, "DROP SCHEMA " + schema + " CASCADE");
                administrator.commit();
            }
        } finally {
            administrator.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authorizedForbiddenHiddenAndCrossTenant(boolean history) throws Exception {
        Object result = read(database, "actor-a", "workflow:read", "instance-a", history);
        if (history) {
            var records = (List<?>) result;
            assertThat(records).hasSize(1);
            var record = (com.tricore.dxos.core.workflow.TransitionRecord) records.getFirst();
            assertThat(record.instanceId()).isEqualTo("instance-a");
            assertThat(record.occurredAt()).isEqualTo(Instant.ofEpochSecond(1, 456));
        } else {
            WorkflowInstance instance = (WorkflowInstance) result;
            assertThat(instance.instanceId()).isEqualTo("instance-a");
            assertThat(instance.resourceReference().resourceId()).isEqualTo("res-a");
            assertThat(instance.createdAt()).isEqualTo(Instant.ofEpochSecond(0, 123));
        }
        deny(WorkflowAccessDecision.FORBIDDEN,
                () -> read(database, "actor-a", "workflow:read-all", "instance-a", history));
        deny(WorkflowAccessDecision.HIDDEN,
                () -> read(database, "actor-b", "workflow:read", "instance-a", history));
        deny(WorkflowAccessDecision.HIDDEN,
                () -> read(database, "actor-a", "workflow:read", "instance-b", history));
        deny(WorkflowAccessDecision.HIDDEN,
                () -> read(database, "actor-a", "", "missing", history));
        deny(WorkflowAccessDecision.HIDDEN,
                () -> read(database, "member", "workflow:read", "instance-a", history));
        sql(administrator, "INSERT INTO " + schema + ".resource_acl VALUES"
                + "('tenant-a','item','res-a','member','workflow:create',true)");
        administrator.commit();
        deny(WorkflowAccessDecision.FORBIDDEN,
                () -> read(database, "member", "workflow:read", "instance-a", history));
        sql(administrator, "INSERT INTO " + schema + ".resource_acl VALUES"
                + "('tenant-a','item','res-a','member','workflow:read',true)");
        administrator.commit();
        assertThat(read(database, "member", "workflow:read", "instance-a", history)).isNotNull();
        sql(administrator, "UPDATE " + schema + ".resource_acl SET granted=false WHERE actor_id='actor-a'");
        administrator.commit();
        deny(WorkflowAccessDecision.FORBIDDEN,
                () -> read(database, "actor-a", "workflow:read", "instance-a", history));
        sql(administrator, "UPDATE " + schema + ".tenant_memberships SET active=false WHERE actor_id='actor-a'");
        administrator.commit();
        deny(WorkflowAccessDecision.HIDDEN,
                () -> read(database, "actor-a", "workflow:read", "instance-a", history));
    }

    @Test
    void existingEmptyHistoryIsAllowedButTombstoneIsHidden() throws Exception {
        assertThat((List<?>) read(database, "actor-a", "workflow:read", "empty-a", true)).isEmpty();
        sql(administrator, "UPDATE " + schema + ".resource_registry SET lifecycle='TOMBSTONED',tombstoned_at=now()"
                + " WHERE resource_id='res-a'");
        administrator.commit();
        for (boolean history : List.of(false, true)) {
            deny(WorkflowAccessDecision.HIDDEN,
                    () -> read(database, "actor-a", "workflow:read", "instance-a", history));
        }
    }

    @Test
    void protectedServiceReverifiesEachCallWithoutReusingAnEarlierActorsGrant() throws Exception {
        CurrentPrincipal principal = mock(CurrentPrincipal.class);
        when(principal.requireVerified()).thenReturn(
                new AuthenticatedPrincipal("actor-a", "test", Set.of(new Authority("workflow:read")), true),
                new AuthenticatedPrincipal("actor-b", "test", Set.of(new Authority("workflow:read")), true));
        var legacy = spy(new PostgresWorkflowPersistence(database, schema));
        var service = new Stage6GuardedReadService(legacy, principal);
        assertThat(service.loadInstance("instance-a").instanceId()).isEqualTo("instance-a");
        deny(WorkflowAccessDecision.HIDDEN, () -> service.loadHistory("instance-a"));
        verify(principal, times(2)).requireVerified();
        verify(legacy, never()).loadInstance(anyString());
        verify(legacy, never()).loadHistory(anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hiddenAndMissingNeverAttemptLocksOrPayloadWhileTargetIsLocked(boolean history) throws Exception {
        DataSource guarded = denialSource(true);
        try (Connection blocker = open(); var workers = Executors.newSingleThreadExecutor()) {
            sql(blocker, "SELECT tenant_id FROM " + schema + ".tenant_guards WHERE tenant_id='tenant-a' FOR UPDATE");
            sql(blocker, "SELECT resource_id FROM " + schema + ".resource_registry WHERE resource_id='res-a' FOR UPDATE");
            sql(blocker, "SELECT instance_id FROM " + schema + ".workflow_instances WHERE instance_id='instance-a' FOR UPDATE");
            try {
                for (String actor : List.of("actor-b", "member")) {
                    for (String id : List.of("instance-a", "missing")) {
                        workers.submit(() -> deny(WorkflowAccessDecision.HIDDEN,
                                () -> read(guarded, actor, "workflow:read", id, history))).get(3, TimeUnit.SECONDS);
                    }
                }
            } finally {
                blocker.rollback();
            }
        }
        verify(guarded, times(4)).getConnection();
        assertThat(read(database, "actor-a", "workflow:read", "instance-a", history)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void revokeFirstDeniesWaitingRead(boolean history) throws Exception {
        try (Connection revoker = open(); var workers = Executors.newSingleThreadExecutor()) {
            lockAndRevoke(revoker);
            AtomicInteger readerPid = new AtomicInteger();
            CountDownLatch ready = new CountDownLatch(1);
            DataSource observed = observedSource(readerPid, ready, null, null);
            var pending = workers.submit(() -> {
                deny(WorkflowAccessDecision.FORBIDDEN,
                        () -> read(observed, "actor-a", "workflow:read", "instance-a", history));
            });
            try {
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                awaitBlocked(readerPid.get());
                assertThat(pending.isDone()).isFalse();
                revoker.commit();
                pending.get(8, TimeUnit.SECONDS);
            } finally {
                revoker.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readFirstHoldsGuardUntilCommitAndNextReadIsDenied(boolean history) throws Exception {
        CountDownLatch commitReached = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var pendingRead = workers.submit(() -> read(
                    observedSource(new AtomicInteger(), new CountDownLatch(1), commitReached, allowCommit),
                    "actor-a", "workflow:read", "instance-a", history));
            try {
                assertThat(commitReached.await(3, TimeUnit.SECONDS)).isTrue();
                AtomicInteger revokerPid = new AtomicInteger();
                CountDownLatch ready = new CountDownLatch(1);
                var pendingRevoke = workers.submit(() -> {
                    try (Connection revoker = open()) {
                        revokerPid.set(number(revoker, "SELECT pg_backend_pid()"));
                        ready.countDown();
                        lockAndRevoke(revoker);
                        revoker.commit();
                    }
                    return null;
                });
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                awaitBlocked(revokerPid.get());
                assertThat(pendingRead.isDone()).isFalse();
                assertThat(pendingRevoke.isDone()).isFalse();
                allowCommit.countDown();
                assertThat(pendingRead.get(8, TimeUnit.SECONDS)).isNotNull();
                pendingRevoke.get(8, TimeUnit.SECONDS);
            } finally {
                allowCommit.countDown();
            }
        }
        deny(WorkflowAccessDecision.FORBIDDEN,
                () -> read(database, "actor-a", "workflow:read", "instance-a", history));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void changedResourceWhileWaitingAbortsInsteadOfFollowingAnotherTenant(boolean history) throws Exception {
        try (Connection mutator = open(); var workers = Executors.newSingleThreadExecutor()) {
            sql(mutator, "SELECT tenant_id FROM " + schema + ".tenant_guards WHERE tenant_id='tenant-a' FOR UPDATE");
            AtomicInteger pid = new AtomicInteger();
            CountDownLatch ready = new CountDownLatch(1);
            DataSource observed = observedSource(pid, ready, null, null);
            var pending = workers.submit(() -> deny(WorkflowAccessDecision.UNAVAILABLE,
                    () -> read(observed, "actor-a", "workflow:read", "instance-a", history)));
            try {
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                awaitBlocked(pid.get());
                // Simulate an unfenced legacy writer; the scoped reader must not follow its new resource.
                sql(mutator, "UPDATE " + schema + ".workflow_instances SET resource_id='res-b' WHERE instance_id='instance-a'");
                mutator.commit();
                pending.get(8, TimeUnit.SECONDS);
            } finally {
                mutator.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void denialDoesNotMaterializePayload(boolean history) throws Exception {
        DataSource guarded = denialSource(false);
        deny(WorkflowAccessDecision.FORBIDDEN,
                () -> read(guarded, "actor-a", "", "instance-a", history));
        deny(WorkflowAccessDecision.HIDDEN,
                () -> read(guarded, "actor-b", "workflow:read", "instance-a", history));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void commitAndRollbackFaultsNeverReleaseDataOrRetry(boolean history) throws Exception {
        // JDBC-proxy simulations; only after-commit sends COMMIT before synthesizing an error.
        for (String fault : List.of("before-commit", "after-commit", "rollback", "connection-loss")) {
            AtomicInteger attempts = new AtomicInteger();
            AtomicInteger commits = new AtomicInteger();
            AtomicInteger rollbacks = new AtomicInteger();
            AtomicInteger closes = new AtomicInteger();
            AtomicReference<String> transactionId = new AtomicReference<>();
            DataSource failing = mock(DataSource.class);
            when(failing.getConnection()).thenAnswer(ignored -> {
                Connection connection = database.getConnection();
                return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("commit")) {
                                attempts.incrementAndGet();
                                transactionId.set(text(connection, "SELECT pg_current_xact_id()"));
                                if (fault.equals("after-commit")) {
                                    connection.commit();
                                    commits.incrementAndGet();
                                }
                                if (fault.equals("connection-loss")) connection.close();
                                throw new SQLException("private " + fault, "08006");
                            }
                            if (method.getName().equals("rollback")) {
                                rollbacks.incrementAndGet();
                                if (fault.equals("rollback")) throw new SQLException("private rollback failure");
                            }
                            if (method.getName().equals("close")) closes.incrementAndGet();
                            try { return method.invoke(connection, args); }
                            catch (InvocationTargetException failure) { throw failure.getCause(); }
                        });
            });
            AtomicReference<Object> escaped = new AtomicReference<>();
            deny(WorkflowAccessDecision.UNAVAILABLE,
                    () -> escaped.set(read(failing, "actor-a", "workflow:read", "instance-a", history)));
            assertThat(escaped.get()).isNull();
            assertThat(attempts.get()).isEqualTo(1);
            assertThat(commits.get()).isEqualTo(fault.equals("after-commit") ? 1 : 0);
            assertThat(rollbacks.get()).isEqualTo(1);
            assertThat(closes.get()).isEqualTo(1);
            verify(failing, times(1)).getConnection();
            try (Connection observer = open()) {
                assertThat(text(observer, "SELECT pg_xact_status('" + transactionId.get() + "'::xid8)"))
                        .isEqualTo(fault.equals("after-commit") ? "committed" : "aborted");
            }
        }
        assertThat(read(database, "actor-a", "workflow:read", "instance-a", history)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void effectiveNetworkTimeoutAndHikariStateAreRestored(boolean history) throws Exception {
        for (int original : List.of(0, 7_000)) {
            Properties properties = new Properties();
            properties.setProperty("currentSchema", schema + ",pg_catalog,pg_temp");
            properties.setProperty("socketTimeout", Integer.toString(original / 1000));
            database.setConnectionProperties(properties);
            try (HikariDataSource pool = pool()) {
                int backend;
                try (Connection connection = pool.getConnection()) {
                    backend = number(connection, "SELECT pg_backend_pid()");
                    assertThat(connection.getNetworkTimeout()).isEqualTo(original);
                }
                AtomicInteger checks = new AtomicInteger();
                DataSource observed = mock(DataSource.class);
                when(observed.getConnection()).thenAnswer(ignored -> {
                    Connection connection = pool.getConnection();
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("createStatement") || method.getName().equals("prepareStatement")) {
                                    assertThat(connection.getNetworkTimeout()).isEqualTo(original == 0 ? 20_000 : original);
                                    checks.incrementAndGet();
                                }
                                try { return method.invoke(connection, args); }
                                catch (InvocationTargetException failure) { throw failure.getCause(); }
                            });
                });
                assertThat(read(observed, "actor-a", "workflow:read", "instance-a", history)).isNotNull();
                assertThat(checks.get()).isPositive();
                try (Connection connection = pool.getConnection()) {
                    assertThat(number(connection, "SELECT pg_backend_pid()")).isEqualTo(backend);
                    assertThat(connection.getNetworkTimeout()).isEqualTo(original);
                    assertThat(connection.getAutoCommit()).isTrue();
                    assertThat(connection.isReadOnly()).isFalse();
                    assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
                    assertThat(text(connection, "SHOW lock_timeout")).isEqualTo("0");
                    assertThat(text(connection, "SHOW statement_timeout")).isEqualTo("0");
                }
                assertThat(read(pool, "actor-a", "workflow:read", "instance-a", history)).isNotNull();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutRestoreFailureDiscardsPhysicalConnectionAndWithholdsResult(boolean history) throws Exception {
        try (HikariDataSource pool = pool()) {
            AtomicInteger backend = new AtomicInteger();
            AtomicInteger aborts = new AtomicInteger();
            DataSource failing = mock(DataSource.class);
            when(failing.getConnection()).thenAnswer(ignored -> {
                Connection connection = pool.getConnection();
                backend.set(number(connection, "SELECT pg_backend_pid()"));
                return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("setNetworkTimeout") && (int) args[1] == 0) {
                                throw new SQLException("private timeout restore failure");
                            }
                            if (method.getName().equals("abort")) aborts.incrementAndGet();
                            try { return method.invoke(connection, args); }
                            catch (InvocationTargetException failure) { throw failure.getCause(); }
                        });
            });
            AtomicReference<Object> escaped = new AtomicReference<>();
            deny(WorkflowAccessDecision.UNAVAILABLE,
                    () -> escaped.set(read(failing, "actor-a", "workflow:read", "instance-a", history)));
            assertThat(escaped.get()).isNull();
            assertThat(aborts.get()).isPositive();
            verify(failing, times(1)).getConnection();
            try (Connection connection = pool.getConnection()) {
                assertThat(number(connection, "SELECT pg_backend_pid()")).isNotEqualTo(backend.get());
                assertThat(connection.getNetworkTimeout()).isZero();
            }
            assertThat(read(pool, "actor-a", "workflow:read", "instance-a", history)).isNotNull();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realPostgresErrorsAndDriverSocketTimeoutNeverReleaseData(boolean history) throws Exception {
        for (String fault : List.of("pre-commit-sql", "statement-timeout", "server-commit", "socket-timeout")) {
            try (HikariDataSource pool = pool()) {
                AtomicInteger commits = new AtomicInteger();
                AtomicInteger rollbacks = new AtomicInteger();
                AtomicInteger backend = new AtomicInteger();
                AtomicReference<String> transactionId = new AtomicReference<>();
                AtomicReference<SQLException> injected = new AtomicReference<>();
                DataSource failing = mock(DataSource.class);
                when(failing.getConnection()).thenAnswer(ignored -> {
                    Connection connection = pool.getConnection();
                    backend.set(number(connection, "SELECT pg_backend_pid()"));
                    if (fault.equals("socket-timeout")) connection.setNetworkTimeout(Runnable::run, 500);
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("rollback")) rollbacks.incrementAndGet();
                                try {
                                    boolean payload = method.getName().equals("prepareStatement")
                                            && (((String) args[0]).contains("SELECT instance_id, definition_id")
                                            || ((String) args[0]).contains("SELECT instance_id, transition_id"));
                                    if (payload && (fault.equals("pre-commit-sql") || fault.equals("statement-timeout"))) {
                                        transactionId.set(text(connection, "SELECT pg_current_xact_id()"));
                                        if (fault.equals("pre-commit-sql")) sql(connection, "SELECT 1/0");
                                        sql(connection, "SET LOCAL statement_timeout='100ms'");
                                        sql(connection, "SELECT pg_sleep(2)");
                                        fail("Expected a real PostgreSQL error");
                                    }
                                    if (method.getName().equals("commit")) {
                                        commits.incrementAndGet();
                                        transactionId.set(text(connection, "SELECT pg_current_xact_id()"));
                                        if (fault.equals("server-commit")) {
                                            sql(connection, "CREATE TEMP TABLE s6_commit_parent(id int PRIMARY KEY) ON COMMIT DROP");
                                            sql(connection, "CREATE TEMP TABLE s6_commit_child(id int REFERENCES s6_commit_parent(id)"
                                                    + " DEFERRABLE INITIALLY DEFERRED) ON COMMIT DROP");
                                            sql(connection, "INSERT INTO s6_commit_child VALUES(1)");
                                            connection.commit();
                                            fail("Expected PostgreSQL to reject COMMIT");
                                        }
                                        // Real pgJDBC socket read timeout, not a synthetic SQLException or TCP disconnect.
                                        sql(connection, "SET LOCAL statement_timeout=0");
                                        sql(connection, "SELECT pg_sleep(3)");
                                        fail("Expected a real driver socket timeout");
                                    }
                                } catch (SQLException failure) {
                                    injected.set(failure);
                                    throw failure;
                                }
                                try { return method.invoke(connection, args); }
                                catch (InvocationTargetException failure) { throw failure.getCause(); }
                            });
                });
                AtomicReference<Object> escaped = new AtomicReference<>();
                deny(WorkflowAccessDecision.UNAVAILABLE,
                        () -> escaped.set(read(failing, "actor-a", "workflow:read", "instance-a", history)));
                assertThat(escaped.get()).isNull();
                assertThat(injected.get().getSQLState()).isEqualTo(switch (fault) {
                    case "pre-commit-sql" -> "22012";
                    case "statement-timeout" -> "57014";
                    case "server-commit" -> "23503";
                    default -> "08006";
                });
                if (fault.equals("socket-timeout")) {
                    assertThat((Throwable) injected.get()).hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
                }
                assertThat(commits.get()).isEqualTo(fault.equals("pre-commit-sql") || fault.equals("statement-timeout") ? 0 : 1);
                assertThat(rollbacks.get()).isEqualTo(1);
                verify(failing, times(1)).getConnection();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
                String status;
                do {
                    try (Connection observer = open()) {
                        status = text(observer, "SELECT pg_xact_status('" + transactionId.get() + "'::xid8)");
                    }
                    if (status.equals("aborted")) break;
                    Thread.sleep(30);
                } while (System.nanoTime() < deadline);
                assertThat(status).isEqualTo("aborted");
                if (fault.equals("socket-timeout")) {
                    try (Connection connection = pool.getConnection()) {
                        assertThat(number(connection, "SELECT pg_backend_pid()")).isNotEqualTo(backend.get());
                        assertThat(connection.getNetworkTimeout()).isZero();
                    }
                }
                assertThat(read(pool, "actor-a", "workflow:read", "instance-a", history)).isNotNull();
            }
        }
    }

    private HikariDataSource pool() {
        HikariDataSource pool = new HikariDataSource();
        pool.setDataSource(database);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(3_000);
        pool.setValidationTimeout(1_000);
        return pool;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void visibleReadLockTimeoutIsUnavailableWithoutPayload(boolean history) throws Exception {
        DataSource guarded = denialSource(false);
        AtomicInteger pid = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(1);
        DataSource observed = mock(DataSource.class);
        when(observed.getConnection()).thenAnswer(ignored -> {
            Connection connection = guarded.getConnection();
            pid.set(number(connection, "SELECT pg_backend_pid()"));
            ready.countDown();
            return connection;
        });
        try (Connection blocker = open(); var workers = Executors.newSingleThreadExecutor()) {
            sql(blocker, "SELECT tenant_id FROM " + schema + ".tenant_guards WHERE tenant_id='tenant-a' FOR UPDATE");
            var pending = workers.submit(() -> deny(WorkflowAccessDecision.UNAVAILABLE,
                    () -> read(observed, "actor-a", "workflow:read", "instance-a", history)));
            try {
                assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
                awaitBlocked(pid.get());
                pending.get(8, TimeUnit.SECONDS);
            } finally {
                blocker.rollback();
            }
        }
        verify(observed, times(1)).getConnection();
        assertThat(read(database, "actor-a", "workflow:read", "instance-a", history)).isNotNull();
    }

    private DataSource denialSource(boolean rejectLocks) throws Exception {
        DataSource guarded = mock(DataSource.class);
        when(guarded.getConnection()).thenAnswer(ignored -> {
            Connection connection = database.getConnection();
            return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            String query = (String) args[0];
                            if (rejectLocks && query.contains(" FOR ")) {
                                throw new AssertionError("Hidden read attempted a target lock");
                            }
                            if (query.contains("SELECT initial_state") || query.contains("SELECT " + "instance_id, definition_id")
                                    || query.contains("SELECT instance_id, transition_id")) {
                                throw new AssertionError("Denied read materialized protected payload");
                            }
                        }
                        try { return method.invoke(connection, args); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
        });
        return guarded;
    }

    private Object read(DataSource source, String actor, String authority, String id, boolean history) {
        CurrentPrincipal principal = mock(CurrentPrincipal.class);
        when(principal.requireVerified()).thenReturn(new AuthenticatedPrincipal(actor, "synthetic test actor",
                authority.isEmpty() ? Set.of() : Set.of(new Authority(authority)), true));
        var service = new Stage6GuardedReadService(new PostgresWorkflowPersistence(source, schema), principal);
        return history ? service.loadHistory(id) : service.loadInstance(id);
    }

    private DataSource observedSource(AtomicInteger pid, CountDownLatch ready,
                                      CountDownLatch commitReached, CountDownLatch allowCommit) throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(ignored -> {
            Connection connection = database.getConnection();
            pid.set(number(connection, "SELECT pg_backend_pid()"));
            ready.countDown();
            if (commitReached == null) return connection;
            return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commit")) {
                            commitReached.countDown();
                            if (!allowCommit.await(8, TimeUnit.SECONDS)) throw new SQLException("test barrier timeout");
                        }
                        try { return method.invoke(connection, args); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
        });
        return source;
    }

    private void lockAndRevoke(Connection connection) throws SQLException {
        sql(connection, "SET LOCAL statement_timeout='8000ms'");
        sql(connection, "SELECT tenant_id FROM " + schema + ".tenant_guards WHERE tenant_id='tenant-a' FOR UPDATE");
        sql(connection, "UPDATE " + schema + ".resource_acl SET granted=false WHERE actor_id='actor-a'");
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
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
        return connection;
    }

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

    private static void deny(WorkflowAccessDecision decision, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(WorkflowAccessException.class,
                failure -> assertThat(failure.decision()).isEqualTo(decision)).hasNoCause();
    }
}
