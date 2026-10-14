package com.tricore.dxos.infrastructure.workflow;

import com.tricore.dxos.core.workflow.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tricore.dxos.core.workflow.WorkflowErrorCode.*;
import static org.assertj.core.api.Assertions.*;

/** Every case migrates an isolated real PostgreSQL schema; no H2 or persistence fake. */
@EnabledIfEnvironmentVariable(named = "WORKFLOW_TEST_JDBC_URL", matches = ".+")
class PostgresWorkflowPersistenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-13T01:02:03.123456789Z");
    private static final ResourceReference RESOURCE = new ResourceReference("document", "doc-1");
    private static final ActorReference ACTOR = new ActorReference("verified-actor");
    private DriverManagerDataSource admin;
    private DriverManagerDataSource database;
    private String schema;
    private PostgresWorkflowPersistence persistence;
    private WorkflowDefinition definition;

    @BeforeAll
    static void suppressDatabaseConnectionLogging() {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.flywaydb"))
                .setLevel(ch.qos.logback.classic.Level.OFF);
    }

    @BeforeEach
    void migrateRealDatabase() {
        schema = "workflow_test_" + UUID.randomUUID().toString().replace("-", "");
        admin = dataSource(false, null);
        database = dataSource(true, schema);
        String phase = "create-schema";
        try {
            sql(admin, "CREATE SCHEMA " + schema);
            phase = "V1-V2";
            WorkflowMigrations.configure(dataSource(false, schema), schema, "classpath:db/migration")
                    .locations("classpath:db/migration").loggers("slf4j").target("2").load().migrate();
            sql(dataSource(false, schema), """
                    INSERT INTO requests (id, title, description, request_type, status, created_at, updated_at)
                    VALUES ('00000000-0000-0000-0000-000000000025', 'kept', 'kept', 'test', 'NEW', now(), now())
                    """);
            phase = "V3-V4";
            WorkflowMigrations.configure(dataSource(false, schema), schema, "classpath:db/migration")
                    .locations("classpath:db/migration").loggers("slf4j").load().migrate();
            // A separately supplied runtime login gets only the adapter's required privileges.
            String runtimeRole;
            try (Connection connection = database.getConnection()) {
                runtimeRole = connection.getMetaData().getUserName().replace("\"", "\"\"");
            }
            var migrator = dataSource(false, schema);
            phase = "grant";
            sql(migrator, "GRANT USAGE ON SCHEMA " + schema + " TO \"" + runtimeRole + "\"");
            sql(migrator, "GRANT SELECT ON ALL TABLES IN SCHEMA " + schema + " TO \"" + runtimeRole + "\"");
            sql(migrator, "GRANT INSERT, UPDATE ON workflow_instances TO \"" + runtimeRole + "\"");
            sql(migrator, "GRANT INSERT ON workflow_transition_history TO \"" + runtimeRole + "\"");
            persistence = new PostgresWorkflowPersistence(database, schema);
            phase = "load-definition";
            definition = persistence.loadDefinition("document-review", 1);
        } catch (SQLException | RuntimeException failure) {
            Throwable rootCause = failure;
            while (rootCause.getCause() != null) rootCause = rootCause.getCause();
            String safeReason = rootCause.getMessage() != null && rootCause.getMessage().startsWith("Workflow ")
                    ? rootCause.getMessage() : rootCause.getClass().getSimpleName();
            throw new AssertionError("Real PostgreSQL fixture setup failed at " + phase + ": " + safeReason
                    + (rootCause instanceof SQLException sql ? " SQLSTATE=" + sql.getSQLState() : ""));
        }
    }

    @AfterEach
    void dropOnlyOwnedSchema() throws SQLException {
        if (admin != null && schema != null) {
            sql(admin, "DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void migrationsUpgradeV1V2AndSeedTwoIndependentDefinitions() throws SQLException {
        assertThat(scalar(database, "SELECT count(*) FROM requests WHERE title = 'kept'")).isEqualTo(1);
        assertThat(scalar(database, "SELECT count(*) FROM flyway_schema_history WHERE success")).isEqualTo(4);
        WorkflowDefinition other = persistence.loadDefinition("incident-resolution", 1);
        assertThat(other).isNotEqualTo(definition);
        WorkflowInstance incident = WorkflowInstance.notStarted("incident", other,
                new ResourceReference("incident", "inc-1"), NOW);
        persistence.createInstance(incident);
        persistence.commitActivation(incident, incident.activate(other, NOW));
        persistence.commitTransition(persistence.loadInstance("incident").transition(other,
                new TransitionCommand("incident", "start", ACTOR, 0), NOW));
        assertThat(persistence.loadInstance("incident").currentState()).isEqualTo("IN_PROGRESS");
        assertThat(definition.initialState()).isEqualTo("DRAFT");
    }

    @Test
    void exactVersionsAndImmutableIdempotentProvisioning() {
        var importer = new PostgresWorkflowPersistence(dataSource(false, schema), schema);
        WorkflowDefinition version2 = new WorkflowDefinition("document-review", 2, "DRAFT",
                Set.of("DRAFT", "APPROVED"), Set.of(new WorkflowTransition("fast-approve", "DRAFT", "APPROVED")));
        importer.provisionDefinition(version2);
        importer.provisionDefinition(version2);
        assertThat(persistence.loadDefinition("document-review", 1)).isEqualTo(definition);
        assertThat(persistence.loadDefinition("document-review", 2)).isEqualTo(version2);
        WorkflowDefinition changed = new WorkflowDefinition("document-review", 2, "APPROVED",
                version2.states(), version2.transitions());
        assertThatThrownBy(() -> importer.provisionDefinition(changed)).isInstanceOf(IllegalArgumentException.class);
        assertThat(persistence.loadDefinition("document-review", 2)).isEqualTo(version2);
        expectCode(DEFINITION_NOT_FOUND, () -> persistence.loadDefinition("document-review", 3));
        expectCode(DEFINITION_NOT_FOUND, () -> persistence.loadDefinition("absent", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE workflow_definitions SET initial_state = 'APPROVED' WHERE definition_id = 'document-review'",
            "DELETE FROM workflow_definitions WHERE definition_id = 'document-review'",
            "UPDATE workflow_definition_states SET state_id = 'NEW' WHERE state_id = 'DRAFT'",
            "DELETE FROM workflow_definition_states WHERE state_id = 'DRAFT'",
            "UPDATE workflow_definition_transitions SET target_state = 'DRAFT' WHERE transition_id = 'approve'",
            "DELETE FROM workflow_definition_transitions WHERE transition_id = 'approve'",
            "INSERT INTO workflow_definition_states VALUES ('document-review', 1, 'LATE')",
            "INSERT INTO workflow_definition_transitions VALUES ('document-review', 1, 'late', 'DRAFT', 'APPROVED')",
            "TRUNCATE workflow_definitions CASCADE",
            "TRUNCATE workflow_definition_states CASCADE",
            "TRUNCATE workflow_definition_transitions"
    })
    void databaseSealsAllDefinitionContent(String mutation) {
        assertThatThrownBy(() -> sql(dataSource(false, schema), mutation)).isInstanceOf(SQLException.class);
        assertThat(persistence.loadDefinition("document-review", 1)).isEqualTo(definition);
    }

    @Test
    void initialStateMustExistAndProvisioningTransactionCannotBeSpoofed() throws SQLException {
        var migrator = dataSource(false, schema);
        assertThatThrownBy(() -> sql(migrator,
                "INSERT INTO workflow_definitions (definition_id, definition_version, initial_state) VALUES ('bad', 1, 'missing')"))
                .isInstanceOf(SQLException.class);
        try (Connection connection = migrator.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO workflow_definitions
                        (definition_id, definition_version, initial_state, provisioning_transaction)
                        VALUES ('sealed', 0, 'ONLY', 0)
                        """);
                statement.executeUpdate("INSERT INTO workflow_definition_states VALUES ('sealed', 0, 'ONLY')");
            }
            connection.commit();
        }
        assertThatThrownBy(() -> sql(migrator, "INSERT INTO workflow_definition_states VALUES ('sealed', 0, 'LATE')"))
                .isInstanceOf(SQLException.class);
        assertThat(persistence.loadDefinition("sealed", 0).states()).containsExactly("ONLY");
    }

    @Test
    void createIsInsertOnlyAndActivationStaysVersionZeroWithoutHistory() {
        WorkflowInstance initial = create("one");
        expectCode(INSTANCE_ALREADY_EXISTS, () -> persistence.createInstance(initial));
        assertSnapshot(initial, persistence.loadInstance("one"));
        assertThat(persistence.loadHistory("one")).isEmpty();
        WorkflowInstance active = initial.activate(definition, NOW);
        assertSnapshot(active, persistence.commitActivation(initial, active));
        assertThat(persistence.loadInstance("one").runtimeVersion()).isZero();
        assertThat(persistence.loadHistory("one")).isEmpty();
        expectCode(VERSION_CONFLICT, () -> persistence.commitActivation(initial, active));
        expectCode(TRANSITION_NOT_ALLOWED, () -> persistence.commitActivation(active, active));
    }

    @Test
    void creationUsesAuthoritativeInitialStateAndDefinition() {
        WorkflowDefinition forged = new WorkflowDefinition(definition.definitionId(), 1, "IN_REVIEW",
                definition.states(), definition.transitions());
        expectCode(TRANSITION_NOT_ALLOWED, () -> persistence.createInstance(
                WorkflowInstance.notStarted("forged", forged, RESOURCE, NOW)));
        expectCode(INSTANCE_NOT_FOUND, () -> persistence.loadInstance("forged"));
        WorkflowInstance initial = WorkflowInstance.notStarted("not-initial", definition, RESOURCE, NOW);
        expectCode(TRANSITION_NOT_ALLOWED, () -> persistence.createInstance(initial.activate(definition, NOW)));
        WorkflowDefinition absent = new WorkflowDefinition("missing", 1, "ONLY", Set.of("ONLY"), Set.of());
        expectCode(DEFINITION_NOT_FOUND, () -> persistence.createInstance(
                WorkflowInstance.notStarted("missing", absent, RESOURCE, NOW)));
        expectCode(INSTANCE_NOT_FOUND, () -> persistence.loadHistory("missing"));
        create("exists");
        expectCode(INSTANCE_ALREADY_EXISTS, () -> persistence.createInstance(
                WorkflowInstance.notStarted("exists", absent, RESOURCE, NOW)));
    }

    @ParameterizedTest
    @CsvSource({
            "id, INSTANCE_NOT_FOUND", "definition, TRANSITION_NOT_ALLOWED", "definition-version, TRANSITION_NOT_ALLOWED",
            "resource-type, TRANSITION_NOT_ALLOWED", "resource-id, TRANSITION_NOT_ALLOWED",
            "state, TRANSITION_NOT_ALLOWED", "version, VERSION_CONFLICT",
            "created, TRANSITION_NOT_ALLOWED", "updated, TRANSITION_NOT_ALLOWED"
    })
    void transitionComparesFullPersistedSource(String field, WorkflowErrorCode error) {
        WorkflowInstance active = activate("source");
        WorkflowDefinition fakeDefinition = sourceDefinition(field);
        WorkflowInstance fake = WorkflowInstance.restore(field.equals("id") ? "missing" : active.instanceId(),
                fakeDefinition,
                new ResourceReference(field.equals("resource-type") ? "other" : RESOURCE.resourceType(),
                        field.equals("resource-id") ? "other" : RESOURCE.resourceId()),
                field.equals("state") ? "IN_REVIEW" : "DRAFT",
                field.equals("version") || field.equals("state") ? 1 : 0, WorkflowLifecycle.ACTIVE,
                field.equals("created") ? NOW.minusNanos(1) : NOW,
                field.equals("updated") ? NOW.plusNanos(1) : NOW);
        // Give both sides version 1 for the state case so the state check, rather than version, rejects it.
        if (field.equals("state")) {
            persistence.commitTransition(proposal(active, "submit", NOW));
            active = persistence.loadInstance("source");
            fake = WorkflowInstance.restore("source", fakeDefinition, RESOURCE, "DRAFT", 1,
                    WorkflowLifecycle.ACTIVE, NOW, NOW);
        }
        TransitionResult forged = proposal(fake, fakeDefinition, "submit", NOW.plusNanos(2));
        expectCode(error, () -> persistence.commitTransition(forged));
        assertSnapshot(active, persistence.loadInstance("source"));
    }

    private WorkflowDefinition sourceDefinition(String field) {
        String id = field.equals("definition") ? "other-definition" : definition.definitionId();
        long version = field.equals("definition-version") ? 2 : definition.definitionVersion();
        return new WorkflowDefinition(id, version, "DRAFT", definition.states(), definition.transitions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "definition", "definition-version", "resource-type", "resource-id", "state", "created"})
    void activationRejectsForgedSource(String field) {
        WorkflowInstance initial = create("activation");
        WorkflowDefinition fakeDefinition = sourceDefinition(field);
        if (field.equals("state")) {
            fakeDefinition = new WorkflowDefinition(definition.definitionId(), 1, "IN_REVIEW",
                    definition.states(), definition.transitions());
        }
        WorkflowInstance fake = WorkflowInstance.notStarted(field.equals("id") ? "missing" : initial.instanceId(),
                fakeDefinition, new ResourceReference(field.equals("resource-type") ? "other" : RESOURCE.resourceType(),
                        field.equals("resource-id") ? "other" : RESOURCE.resourceId()),
                field.equals("created") ? NOW.minusNanos(1) : NOW);
        WorkflowInstance activated = fake.activate(fakeDefinition, NOW);
        expectCode(field.equals("id") ? INSTANCE_NOT_FOUND : TRANSITION_NOT_ALLOWED,
                () -> persistence.commitActivation(fake, activated));
        assertSnapshot(initial, persistence.loadInstance("activation"));
    }

    @Test
    void activationChecksLifecycleVersionProposalAndBackwardsTime() {
        WorkflowInstance initial = create("activation");
        WorkflowInstance active = initial.activate(definition, NOW);
        expectCode(VERSION_CONFLICT, () -> persistence.commitActivation(active, active));
        WorkflowInstance newer = WorkflowInstance.restore("activation", definition, RESOURCE, "DRAFT", 1,
                WorkflowLifecycle.ACTIVE, NOW, NOW);
        expectCode(VERSION_CONFLICT, () -> persistence.commitActivation(newer, newer));
        WorkflowInstance wrongResource = WorkflowInstance.restore("activation", definition,
                new ResourceReference("other", "other"), "DRAFT", 0, WorkflowLifecycle.ACTIVE, NOW, NOW);
        expectCode(TRANSITION_NOT_ALLOWED, () -> persistence.commitActivation(initial, wrongResource));
        WorkflowInstance earlier = WorkflowInstance.restore("activation", definition, RESOURCE, "DRAFT", 0,
                WorkflowLifecycle.ACTIVE, NOW.minusNanos(1), NOW.minusNanos(1));
        expectCode(TRANSITION_NOT_ALLOWED, () -> persistence.commitActivation(initial, earlier));
        assertSnapshot(initial, persistence.loadInstance("activation"));
    }

    @Test
    void authoritativeDefinitionRejectsForgedTransitionTargetAndUnknownTransition() {
        WorkflowInstance active = activate("forged");
        WorkflowDefinition wrong = new WorkflowDefinition(definition.definitionId(), 1, "DRAFT",
                definition.states(), Set.of(new WorkflowTransition("submit", "DRAFT", "APPROVED")));
        expectCode(TRANSITION_NOT_ALLOWED, () -> persistence.commitTransition(proposal(active, wrong, "submit", NOW)));
        WorkflowDefinition invented = new WorkflowDefinition(definition.definitionId(), 1, "DRAFT",
                definition.states(), Set.of(new WorkflowTransition("invented", "DRAFT", "APPROVED")));
        expectCode(INVALID_TRANSITION, () -> persistence.commitTransition(proposal(active, invented, "invented", NOW)));
        assertSnapshot(active, persistence.loadInstance("forged"));
        assertThat(persistence.loadHistory("forged")).isEmpty();
    }

    @Test
    void historyIsImmutableOrderedContiguousAndTerminalReplayHasCorrectPrecedence() {
        WorkflowInstance active = activate("history");
        TransitionResult first = proposal(active, "submit", NOW);
        assertSnapshot(first.instance(), persistence.commitTransition(first).instance());
        expectCode(VERSION_CONFLICT, () -> persistence.commitTransition(first));
        TransitionResult second = proposal(persistence.loadInstance("history"), "approve", NOW);
        persistence.commitTransition(second);
        assertThat(persistence.loadInstance("history").lifecycle()).isEqualTo(WorkflowLifecycle.COMPLETED);
        List<TransitionRecord> history = persistence.loadHistory("history");
        assertThat(history).containsExactly(first.record(), second.record());
        assertThatThrownBy(() -> history.clear()).isInstanceOf(UnsupportedOperationException.class);
        expectCode(VERSION_CONFLICT, () -> persistence.commitTransition(second));
        WorkflowInstance completed = persistence.loadInstance("history");
        expectCode(INSTANCE_COMPLETED, () -> persistence.commitActivation(completed, completed));
    }

    @Test
    void sqlInjectionIdentifiersRemainData() {
        WorkflowInstance initial = create("'; DROP TABLE workflow_instances; --");
        assertSnapshot(initial, persistence.loadInstance(initial.instanceId()));
        assertThat(persistence.loadHistory(initial.instanceId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1000000000-01-01T00:00:00Z", "+1000000000-12-31T23:59:59.999999999Z",
            "1969-12-31T23:59:59.999999999Z", "2026-10-13T01:02:03.123456789Z"})
    void allJavaInstantSecondsAndNanosRoundTripExactly(String text) throws SQLException {
        Instant instant = Instant.parse(text);
        WorkflowInstance initial = WorkflowInstance.notStarted("precision", definition, RESOURCE, instant);
        persistence.createInstance(initial);
        WorkflowInstance active = persistence.commitActivation(initial, initial.activate(definition, instant));
        TransitionResult committed = persistence.commitTransition(proposal(active, "submit", instant));
        assertSnapshot(committed.instance(), persistence.loadInstance("precision"));
        assertThat(persistence.loadHistory("precision").getFirst().occurredAt()).isEqualTo(instant);
        assertThat(scalar(database, "SELECT updated_at_seconds FROM workflow_instances WHERE instance_id = 'precision'"))
                .isEqualTo(instant.getEpochSecond());
        assertThat(scalar(database, "SELECT updated_at_nanos FROM workflow_instances WHERE instance_id = 'precision'"))
                .isEqualTo(instant.getNano());
    }

    @Test
    void databaseRejectsOutOfRangeInstantAndNanos() throws SQLException {
        create("range");
        for (String mutation : List.of(
                "UPDATE workflow_instances SET created_at_seconds = -31557014167219201",
                "UPDATE workflow_instances SET updated_at_seconds = 31556889864403200",
                "UPDATE workflow_instances SET updated_at_nanos = 1000000000",
                "UPDATE workflow_instances SET created_at_nanos = -1")) {
            assertThatThrownBy(() -> sql(dataSource(false, schema), mutation)).isInstanceOf(SQLException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "activate", "transition"})
    void twoIndependentConnectionsHaveExactlyOneWinner(String operation) throws Exception {
        var other = new PostgresWorkflowPersistence(dataSource(true, schema), schema);
        Callable<Object> left;
        Callable<Object> right;
        if (operation.equals("create")) {
            WorkflowInstance initial = WorkflowInstance.notStarted("race", definition, RESOURCE, NOW);
            left = () -> persistence.createInstance(initial);
            right = () -> other.createInstance(initial);
        } else if (operation.equals("activate")) {
            WorkflowInstance initial = create("race");
            WorkflowInstance active = initial.activate(definition, NOW);
            left = () -> persistence.commitActivation(initial, active);
            right = () -> other.commitActivation(initial, active);
        } else {
            TransitionResult proposal = proposal(activate("race"), "submit", NOW);
            left = () -> persistence.commitTransition(proposal);
            right = () -> other.commitTransition(proposal);
        }
        List<Object> results = race(left, right);
        WorkflowErrorCode loser = operation.equals("create") ? INSTANCE_ALREADY_EXISTS : VERSION_CONFLICT;
        assertThat(results.stream().filter(loser::equals)).hasSize(1);
        assertThat(results.stream().filter(result -> !(result instanceof WorkflowErrorCode))).hasSize(1);
        assertThat(persistence.loadInstance("race").runtimeVersion()).isEqualTo(operation.equals("transition") ? 1 : 0);
        assertThat(persistence.loadHistory("race")).hasSize(operation.equals("transition") ? 1 : 0);
    }

    @Test
    void historyInsertFailureRollsBackInstanceAndDoesNotRetry() throws SQLException {
        WorkflowInstance active = activate("rollback");
        rejectHistoryInsert();
        AtomicInteger connections = new AtomicInteger();
        var faulty = faultAdapter("count", connections);
        expectCode(OPERATION_FAILURE, () -> faulty.commitTransition(proposal(active, "submit", NOW)));
        assertThat(connections).hasValue(1);
        assertSnapshot(active, persistence.loadInstance("rollback"));
        assertThat(persistence.loadHistory("rollback")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"commit-before", "commit-after"})
    void lostCommitResponseIsUnknownAndAtomicWithoutAutomaticRetry(String fault) {
        WorkflowInstance active = activate("unknown");
        TransitionResult transition = proposal(active, "submit", NOW);
        AtomicInteger connections = new AtomicInteger();
        expectCode(COMMIT_OUTCOME_UNKNOWN,
                () -> faultAdapter(fault, connections).commitTransition(transition));
        assertThat(connections).hasValue(1);
        if (fault.equals("commit-after")) {
            assertSnapshot(transition.instance(), persistence.loadInstance("unknown"));
            assertThat(persistence.loadHistory("unknown")).containsExactly(transition.record());
            expectCode(VERSION_CONFLICT, () -> persistence.commitTransition(transition));
        } else {
            assertSnapshot(active, persistence.loadInstance("unknown"));
            assertThat(persistence.loadHistory("unknown")).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "activate"})
    void lostCommitResponseForOtherWritesIsAlsoUnknown(String operation) {
        WorkflowInstance initial = WorkflowInstance.notStarted("unknown", definition, RESOURCE, NOW);
        var faulty = faultAdapter("commit-after", new AtomicInteger());
        if (operation.equals("create")) {
            expectCode(COMMIT_OUTCOME_UNKNOWN, () -> faulty.createInstance(initial));
            assertSnapshot(initial, persistence.loadInstance("unknown"));
            expectCode(INSTANCE_ALREADY_EXISTS, () -> persistence.createInstance(initial));
        } else {
            persistence.createInstance(initial);
            WorkflowInstance active = initial.activate(definition, NOW);
            expectCode(COMMIT_OUTCOME_UNKNOWN, () -> faulty.commitActivation(initial, active));
            assertSnapshot(active, persistence.loadInstance("unknown"));
            expectCode(VERSION_CONFLICT, () -> persistence.commitActivation(initial, active));
        }
        assertThat(persistence.loadHistory("unknown")).isEmpty();
    }

    @Test
    void failedRollbackCannotClaimOperationFailure() throws SQLException {
        WorkflowInstance active = activate("rollback");
        rejectHistoryInsert();
        expectCode(COMMIT_OUTCOME_UNKNOWN, () -> faultAdapter("rollback", new AtomicInteger())
                .commitTransition(proposal(active, "submit", NOW)));
        assertSnapshot(active, persistence.loadInstance("rollback"));
        assertThat(persistence.loadHistory("rollback")).isEmpty();
    }

    @Test
    void cleanupFailureDoesNotHideConfirmedDurableCommit() {
        WorkflowInstance initial = WorkflowInstance.notStarted("cleanup", definition, RESOURCE, NOW);
        assertSnapshot(initial, faultAdapter("close-after", new AtomicInteger()).createInstance(initial));
        assertSnapshot(initial, persistence.loadInstance("cleanup"));
    }

    @Test
    void ambientSpringTransactionCannotDeferPortCommit() {
        var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(database);
        var outer = new org.springframework.transaction.support.TransactionTemplate(manager);
        outer.executeWithoutResult(status -> {
            create("independent");
            status.setRollbackOnly();
        });
        assertThat(persistence.loadInstance("independent").instanceId()).isEqualTo("independent");
    }

    @Test
    void springProvidesOneProductionPortAndDataSurvivesBackendRestart() {
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(PostgresWorkflowPersistence.class, WorkflowPersistenceConfiguration.class)
                .withBean(DataSource.class, () -> database)
                .withPropertyValues("dxos.workflow.schema=" + schema);
        runner.run(context -> {
            assertThat(context).hasSingleBean(WorkflowPersistencePort.class).hasSingleBean(WorkflowRuntime.class);
            var runtime = context.getBean(WorkflowRuntime.class);
            runtime.createInstance("restart", "document-review", 1, RESOURCE);
            runtime.activateInstance("restart");
            runtime.executeTransition(new TransitionCommand("restart", "submit", ACTOR, 0));
        });
        runner.run(context -> {
            var runtime = context.getBean(WorkflowRuntime.class);
            assertThat(runtime.loadInstance("restart").runtimeVersion()).isEqualTo(1);
            assertThat(runtime.loadHistory("restart")).hasSize(1);
            assertThat(context).doesNotHaveBean(com.tricore.dxos.gateway.workflow.controller.WorkflowController.class);
        });
    }

    @Test
    void validatesStructuralArgumentsWithoutConnecting() {
        var adapter = faultAdapter("connect", new AtomicInteger());
        assertThatThrownBy(() -> adapter.loadDefinition(" ", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.loadDefinition("valid", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.loadInstance(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.loadHistory(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.createInstance(null)).isInstanceOf(NullPointerException.class);
        expectCode(OPERATION_FAILURE, () -> adapter.loadDefinition("valid", 1));
        expectCode(OPERATION_FAILURE, () -> adapter.createInstance(
                WorkflowInstance.notStarted("failed", definition, RESOURCE, NOW)));
        expectCode(OPERATION_FAILURE, () -> faultAdapter("connect-runtime", new AtomicInteger())
                .loadInstance("valid"));
    }

    @Test
    void runtimeLoginCannotProvisionDeleteOrOwnWorkflowTables() throws SQLException {
        String runtimeUser = required("WORKFLOW_TEST_DB_USER");
        String migrationUser = System.getenv("WORKFLOW_TEST_MIGRATION_USER");
        org.junit.jupiter.api.Assumptions.assumeTrue(migrationUser != null && !runtimeUser.equals(migrationUser),
                "Supply a separate migration login to verify least-privilege runtime permissions");
        WorkflowInstance initial = create("permissions");
        for (String forbidden : List.of(
                "INSERT INTO workflow_definitions (definition_id, definition_version, initial_state) VALUES ('forbidden', 1, 'ONLY')",
                "INSERT INTO workflow_definition_states VALUES ('document-review', 1, 'LATE')",
                "DELETE FROM workflow_instances",
                "DELETE FROM workflow_transition_history",
                "DROP TABLE workflow_instances CASCADE")) {
            assertThatThrownBy(() -> sql(database, forbidden)).isInstanceOf(SQLException.class);
        }
        expectCode(OPERATION_FAILURE, () -> persistence.provisionDefinition(
                new WorkflowDefinition("forbidden", 1, "ONLY", Set.of("ONLY"), Set.of())));
        assertSnapshot(initial, persistence.loadInstance("permissions"));
    }

    private void rejectHistoryInsert() throws SQLException {
        var migrator = dataSource(false, schema);
        sql(migrator, """
                CREATE FUNCTION fail_history() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'private provider details'; END; $$
                """);
        sql(migrator, """
                CREATE TRIGGER fail_history BEFORE INSERT ON workflow_transition_history
                FOR EACH ROW EXECUTE FUNCTION fail_history()
                """);
    }

    private PostgresWorkflowPersistence faultAdapter(String fault, AtomicInteger connections) {
        return new PostgresWorkflowPersistence(new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                connections.incrementAndGet();
                if (fault.equals("connect")) {
                    throw new SQLException("private credentials must never escape", "08001");
                }
                if (fault.equals("connect-runtime")) {
                    throw new IllegalArgumentException("private credentials must never escape");
                }
                Connection real = database.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                            if (method.getName().equals("commit")) {
                                if (fault.equals("commit-before")) {
                                    throw new SQLException("lost connection before response", "08006");
                                }
                                if (fault.equals("commit-after")) {
                                    real.commit();
                                    throw new SQLException("lost response after durable commit", "08006");
                                }
                            }
                            if (method.getName().equals("rollback") && fault.equals("rollback")) {
                                throw new SQLException("rollback response unavailable", "08006");
                            }
                            if (method.getName().equals("close") && fault.equals("close-after")) {
                                real.close();
                                throw new SQLException("pool cleanup failure");
                            }
                            try {
                                return method.invoke(real, arguments);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }
        }, schema);
    }

    private WorkflowInstance create(String id) {
        return persistence.createInstance(WorkflowInstance.notStarted(id, definition, RESOURCE, NOW));
    }

    private WorkflowInstance activate(String id) {
        WorkflowInstance initial = create(id);
        return persistence.commitActivation(initial, initial.activate(definition, NOW));
    }

    private TransitionResult proposal(WorkflowInstance instance, String transition, Instant time) {
        return proposal(instance, definition, transition, time);
    }

    private TransitionResult proposal(WorkflowInstance instance, WorkflowDefinition exact, String transition, Instant time) {
        return instance.transition(exact,
                new TransitionCommand(instance.instanceId(), transition, ACTOR, instance.runtimeVersion()), time);
    }

    private static void assertSnapshot(WorkflowInstance expected, WorkflowInstance actual) {
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    private static void expectCode(WorkflowErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(WorkflowException.class)
                .hasMessage(code.name()).hasNoCause();
    }

    private static List<Object> race(Callable<Object> left, Callable<Object> right) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> raced(start, left));
            var second = workers.submit(() -> raced(start, right));
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private static Object raced(CyclicBarrier start, Callable<Object> operation) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            return operation.call();
        } catch (WorkflowException failure) {
            return failure.code();
        }
    }

    private static DriverManagerDataSource dataSource(boolean runtime, String schema) {
        DriverManagerDataSource result = new DriverManagerDataSource();
        String url = required("WORKFLOW_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost|\\[::1\\]):[0-9]+/[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Destructive integration tests require a local disposable database URL");
        }
        result.setUrl(url);
        String user = System.getenv(runtime ? "WORKFLOW_TEST_DB_USER" : "WORKFLOW_TEST_MIGRATION_USER");
        String password = System.getenv(runtime ? "WORKFLOW_TEST_DB_PASSWORD" : "WORKFLOW_TEST_MIGRATION_PASSWORD");
        result.setUsername(user == null ? required("WORKFLOW_TEST_DB_USER") : user);
        result.setPassword(password == null ? required("WORKFLOW_TEST_DB_PASSWORD") : password);
        if (schema != null) {
            Properties properties = new Properties();
            properties.setProperty("currentSchema", schema);
            result.setConnectionProperties(properties);
        }
        return result;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing test environment variable: " + name);
        }
        return value;
    }

    private static void sql(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long scalar(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
