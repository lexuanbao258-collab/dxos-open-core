package com.tricore.dxos.infrastructure.workflow;

import com.tricore.dxos.core.workflow.ActorReference;
import com.tricore.dxos.core.workflow.ResourceReference;
import com.tricore.dxos.core.workflow.TransitionCommand;
import com.tricore.dxos.core.workflow.TransitionRecord;
import com.tricore.dxos.core.workflow.TransitionResult;
import com.tricore.dxos.core.workflow.WorkflowDefinition;
import com.tricore.dxos.core.workflow.WorkflowErrorCode;
import com.tricore.dxos.core.workflow.WorkflowException;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowLifecycle;
import com.tricore.dxos.core.workflow.WorkflowPersistencePort;
import com.tricore.dxos.core.workflow.WorkflowTransition;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns its JDBC transactions: a Spring transaction must not defer this port's commit. */
@Component
public final class PostgresWorkflowPersistence implements WorkflowPersistencePort {
    private static final String INSTANCE_COLUMNS = """
            instance_id, definition_id, definition_version, resource_type, resource_id,
            current_state, runtime_version, lifecycle, created_at_seconds, created_at_nanos,
            updated_at_seconds, updated_at_nanos
            """;
    private final DataSource dataSource;
    private final WorkflowSchema schema;

    public PostgresWorkflowPersistence(DataSource dataSource,
                                       @Value("${dxos.workflow.schema}") String expectedSchema) {
        this.schema = new WorkflowSchema(expectedSchema);
        this.dataSource = schema.guard(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public WorkflowDefinition loadDefinition(String definitionId, long definitionVersion) {
        requireKey(definitionId, definitionVersion);
        return transaction(false, connection -> definition(connection, definitionId, definitionVersion));
    }

    /** Infrastructure-only import. A key is insert-only; identical imports are idempotent. */
    public WorkflowDefinition provisionDefinition(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        WorkflowDefinition persisted = transaction(true, connection -> {
            int inserted = update(connection, """
                    INSERT INTO %s.workflow_definitions (definition_id, definition_version, initial_state)
                    VALUES (?, ?, ?) ON CONFLICT (definition_id, definition_version) DO NOTHING
                    """, definition.definitionId(), definition.definitionVersion(), definition.initialState());
            if (inserted == 0) {
                return definition(connection, definition.definitionId(), definition.definitionVersion());
            }
            for (String state : definition.states()) {
                update(connection, """
                        INSERT INTO %s.workflow_definition_states (definition_id, definition_version, state_id)
                        VALUES (?, ?, ?)
                        """, definition.definitionId(), definition.definitionVersion(), state);
            }
            for (WorkflowTransition transition : definition.transitions()) {
                update(connection, """
                        INSERT INTO %s.workflow_definition_transitions
                        (definition_id, definition_version, transition_id, source_state, target_state)
                        VALUES (?, ?, ?, ?, ?)
                        """, definition.definitionId(), definition.definitionVersion(), transition.transitionId(),
                        transition.sourceState(), transition.targetState());
            }
            return definition;
        });
        if (!definition.equals(persisted)) {
            throw new IllegalArgumentException("definition version must be immutable");
        }
        return persisted;
    }

    @Override
    public WorkflowInstance createInstance(WorkflowInstance initial) {
        Objects.requireNonNull(initial, "initial");
        return transaction(true, connection -> {
            try (PreparedStatement statement = prepare(connection,
                    "SELECT instance_id FROM %s.workflow_instances WHERE instance_id = ?", initial.instanceId());
                 ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    throw new WorkflowException(WorkflowErrorCode.INSTANCE_ALREADY_EXISTS);
                }
            }
            WorkflowDefinition definition = definition(connection, initial.definitionId(), initial.definitionVersion());
            WorkflowInstance canonical = WorkflowInstance.notStarted(
                    initial.instanceId(), definition, initial.resourceReference(), initial.createdAt());
            requireSnapshot(initial, canonical);
            int inserted = update(connection, "INSERT INTO %s.workflow_instances (" + INSTANCE_COLUMNS + """
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (instance_id) DO NOTHING
                    """, initial.instanceId(), initial.definitionId(), initial.definitionVersion(),
                    initial.resourceReference().resourceType(), initial.resourceReference().resourceId(),
                    initial.currentState(), initial.runtimeVersion(), initial.lifecycle().name(),
                    initial.createdAt().getEpochSecond(), initial.createdAt().getNano(),
                    initial.updatedAt().getEpochSecond(), initial.updatedAt().getNano());
            if (inserted != 1) {
                throw new WorkflowException(WorkflowErrorCode.INSTANCE_ALREADY_EXISTS);
            }
            return canonical;
        });
    }

    @Override
    public WorkflowInstance commitActivation(WorkflowInstance expected, WorkflowInstance activated) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(activated, "activated");
        return transaction(true, connection -> {
            WorkflowInstance persisted = instance(connection, expected.instanceId(), true);
            if (persisted.runtimeVersion() != expected.runtimeVersion() || persisted.lifecycle() != expected.lifecycle()) {
                throw new WorkflowException(WorkflowErrorCode.VERSION_CONFLICT);
            }
            if (persisted.lifecycle() == WorkflowLifecycle.COMPLETED) {
                throw new WorkflowException(WorkflowErrorCode.INSTANCE_COMPLETED);
            }
            requireSnapshot(persisted, expected);
            if (persisted.lifecycle() != WorkflowLifecycle.NOT_STARTED
                    || activated.updatedAt().isBefore(expected.updatedAt())) {
                throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
            }
            WorkflowDefinition definition = definition(connection, persisted.definitionId(), persisted.definitionVersion());
            WorkflowInstance canonical = persisted.activate(definition, activated.updatedAt());
            requireSnapshot(activated, canonical);
            updateInstance(connection, canonical);
            return canonical;
        });
    }

    @Override
    public WorkflowInstance loadInstance(String instanceId) {
        requireText(instanceId);
        return transaction(false, connection -> instance(connection, instanceId, false));
    }

    @Override
    public List<TransitionRecord> loadHistory(String instanceId) {
        requireText(instanceId);
        return transaction(false, connection -> {
            // REPEATABLE READ gives the existence check and history one database snapshot.
            try (PreparedStatement statement = prepare(connection,
                    "SELECT instance_id FROM %s.workflow_instances WHERE instance_id = ?", instanceId);
                 ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new WorkflowException(WorkflowErrorCode.INSTANCE_NOT_FOUND);
                }
            }
            List<TransitionRecord> history = new ArrayList<>();
            try (PreparedStatement statement = prepare(connection, """
                    SELECT instance_id, transition_id, source_state, target_state, actor_id,
                           resulting_runtime_version, occurred_at_seconds, occurred_at_nanos
                    FROM %s.workflow_transition_history WHERE instance_id = ? ORDER BY resulting_runtime_version
                    """, instanceId); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    history.add(new TransitionRecord(rows.getString("instance_id"), rows.getString("transition_id"),
                            rows.getString("source_state"), rows.getString("target_state"),
                            new ActorReference(rows.getString("actor_id")), rows.getLong("resulting_runtime_version"),
                            instant(rows, "occurred_at")));
                }
            }
            return List.copyOf(history);
        });
    }

    @Override
    public TransitionResult commitTransition(TransitionResult transition) {
        Objects.requireNonNull(transition, "transition");
        return transaction(true, connection -> {
            WorkflowInstance persisted = instance(connection, transition.previousInstance().instanceId(), true);
            if (persisted.runtimeVersion() != transition.expectedVersion()) {
                throw new WorkflowException(WorkflowErrorCode.VERSION_CONFLICT);
            }
            if (persisted.lifecycle() == WorkflowLifecycle.COMPLETED) {
                throw new WorkflowException(WorkflowErrorCode.INSTANCE_COMPLETED);
            }
            requireSnapshot(persisted, transition.previousInstance());
            WorkflowDefinition definition = definition(connection, persisted.definitionId(), persisted.definitionVersion());
            TransitionRecord record = transition.record();
            TransitionResult canonical = persisted.transition(definition,
                    new TransitionCommand(persisted.instanceId(), record.transitionId(), record.actorReference(),
                            transition.expectedVersion()), record.occurredAt());
            requireSnapshot(transition.instance(), canonical.instance());
            if (!record.equals(canonical.record())) {
                throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
            }
            updateInstance(connection, canonical.instance());
            if (update(connection, """
                    INSERT INTO %s.workflow_transition_history
                    (instance_id, resulting_runtime_version, transition_id, source_state, target_state,
                     actor_id, occurred_at_seconds, occurred_at_nanos) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, record.instanceId(), record.resultingRuntimeVersion(), record.transitionId(),
                    record.sourceState(), record.targetState(), record.actorReference().actorId(),
                    record.occurredAt().getEpochSecond(), record.occurredAt().getNano()) != 1) {
                throw new SQLException();
            }
            return canonical;
        });
    }

    private WorkflowDefinition definition(Connection connection, String id, long version) throws SQLException {
        String initialState;
        try (PreparedStatement statement = prepare(connection, """
                SELECT initial_state FROM %s.workflow_definitions WHERE definition_id = ? AND definition_version = ?
                """, id, version); ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new WorkflowException(WorkflowErrorCode.DEFINITION_NOT_FOUND);
            }
            initialState = rows.getString("initial_state");
        }
        Set<String> states = new HashSet<>();
        try (PreparedStatement statement = prepare(connection, """
                SELECT state_id FROM %s.workflow_definition_states WHERE definition_id = ? AND definition_version = ?
                """, id, version); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                states.add(rows.getString("state_id"));
            }
        }
        Set<WorkflowTransition> transitions = new HashSet<>();
        try (PreparedStatement statement = prepare(connection, """
                SELECT transition_id, source_state, target_state FROM %s.workflow_definition_transitions
                WHERE definition_id = ? AND definition_version = ?
                """, id, version); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                transitions.add(new WorkflowTransition(rows.getString("transition_id"),
                        rows.getString("source_state"), rows.getString("target_state")));
            }
        }
        return new WorkflowDefinition(id, version, initialState, states, transitions);
    }

    private WorkflowInstance instance(Connection connection, String id, boolean lock) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                "SELECT " + INSTANCE_COLUMNS + " FROM %s.workflow_instances WHERE instance_id = ?"
                        + (lock ? " FOR UPDATE" : ""), id); ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new WorkflowException(WorkflowErrorCode.INSTANCE_NOT_FOUND);
            }
            WorkflowDefinition definition = definition(connection,
                    rows.getString("definition_id"), rows.getLong("definition_version"));
            return WorkflowInstance.restore(rows.getString("instance_id"), definition,
                    new ResourceReference(rows.getString("resource_type"), rows.getString("resource_id")),
                    rows.getString("current_state"), rows.getLong("runtime_version"),
                    WorkflowLifecycle.valueOf(rows.getString("lifecycle")),
                    instant(rows, "created_at"), instant(rows, "updated_at"));
        }
    }

    private void updateInstance(Connection connection, WorkflowInstance instance) throws SQLException {
        if (update(connection, """
                UPDATE %s.workflow_instances SET current_state = ?, runtime_version = ?, lifecycle = ?,
                    updated_at_seconds = ?, updated_at_nanos = ? WHERE instance_id = ?
                """, instance.currentState(), instance.runtimeVersion(), instance.lifecycle().name(),
                instance.updatedAt().getEpochSecond(), instance.updatedAt().getNano(), instance.instanceId()) != 1) {
            throw new SQLException();
        }
    }

    private static Instant instant(ResultSet rows, String prefix) throws SQLException {
        return Instant.ofEpochSecond(rows.getLong(prefix + "_seconds"), rows.getInt(prefix + "_nanos"));
    }

    private static void requireSnapshot(WorkflowInstance left, WorkflowInstance right) {
        if (!left.instanceId().equals(right.instanceId())
                || !left.definitionId().equals(right.definitionId())
                || left.definitionVersion() != right.definitionVersion()
                || !left.resourceReference().equals(right.resourceReference())
                || !left.currentState().equals(right.currentState())
                || left.runtimeVersion() != right.runtimeVersion()
                || left.lifecycle() != right.lifecycle()
                || !left.createdAt().equals(right.createdAt())
                || !left.updatedAt().equals(right.updatedAt())) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
    }

    private static void requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
    }

    private static void requireKey(String id, long version) {
        requireText(id);
        if (version < 0) {
            throw new IllegalArgumentException("definition version must not be negative");
        }
    }

    private PreparedStatement prepare(Connection connection, String sql, Object... values) throws SQLException {
        // Only the validated identifier is formatted; all data remains bound parameters.
        PreparedStatement statement = connection.prepareStatement(sql.formatted(schema.quoted()));
        try {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        } catch (SQLException | RuntimeException failure) {
            try {
                statement.close();
            } catch (SQLException ignored) {
                // Preserve the original failure; the transaction boundary handles rollback.
            }
            throw failure;
        }
    }

    private int update(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, values)) {
            return statement.executeUpdate();
        }
    }

    private <T> T transaction(boolean write, SqlWork<T> work) {
        Connection connection = null;
        boolean started = false;
        boolean committing = false;
        try {
            connection = dataSource.getConnection();
            if (!connection.getAutoCommit()) {
                throw new SQLException();
            }
            connection.setTransactionIsolation(write ? Connection.TRANSACTION_READ_COMMITTED
                    : Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(!write);
            connection.setAutoCommit(false);
            started = true;
            if (write) {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET LOCAL synchronous_commit = on");
                    try (ResultSet settings = statement.executeQuery(
                            "SELECT current_setting('fsync'), current_setting('full_page_writes')")) {
                        if (!settings.next() || !"on".equals(settings.getString(1))
                                || !"on".equals(settings.getString(2))) {
                            throw new SQLException();
                        }
                    }
                }
            }
            T result = work.run(connection);
            committing = true;
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException failure) {
            if (write && committing) {
                // A later successful rollback cannot disprove an earlier successful COMMIT.
                throw new WorkflowException(WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN);
            }
            if (started) {
                try {
                    connection.rollback();
                } catch (SQLException | RuntimeException rollbackFailure) {
                    throw new WorkflowException(write ? WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN
                            : WorkflowErrorCode.OPERATION_FAILURE);
                }
            }
            if (failure instanceof WorkflowException workflowFailure) {
                throw workflowFailure;
            }
            throw new WorkflowException(WorkflowErrorCode.OPERATION_FAILURE);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException | RuntimeException ignored) {
                    // Cleanup cannot change a confirmed durable commit or its classification.
                }
            }
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
