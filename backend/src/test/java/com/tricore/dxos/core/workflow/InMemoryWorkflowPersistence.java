package com.tricore.dxos.core.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Contract fake only: single map replacement models, but does not prove, database atomicity. */
final class InMemoryWorkflowPersistence implements WorkflowPersistencePort {
    private record DefinitionKey(String id, long version) {}
    private record Stored(WorkflowInstance instance, List<TransitionRecord> history) {}

    private final Map<DefinitionKey, WorkflowDefinition> definitions = new HashMap<>();
    private final Map<String, Stored> instances = new HashMap<>();
    private boolean failNextCommit;
    private boolean loseNextCommitResponse;

    synchronized void provision(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        DefinitionKey key = new DefinitionKey(definition.definitionId(), definition.definitionVersion());
        WorkflowDefinition existing = definitions.putIfAbsent(key, definition);
        if (existing != null && !existing.equals(definition)) {
            throw new IllegalArgumentException("definition version must be immutable");
        }
    }

    synchronized void failNextCommit() {
        failNextCommit = true;
    }

    synchronized void loseNextCommitResponse() {
        loseNextCommitResponse = true;
    }

    @Override
    public synchronized WorkflowDefinition loadDefinition(String definitionId, long definitionVersion) {
        WorkflowValues.text(definitionId, "definitionId");
        WorkflowValues.nonNegative(definitionVersion, "definitionVersion");
        WorkflowDefinition definition = definitions.get(new DefinitionKey(definitionId, definitionVersion));
        if (definition == null) throw new WorkflowException(WorkflowErrorCode.DEFINITION_NOT_FOUND);
        return definition;
    }

    @Override
    public synchronized WorkflowInstance createInstance(WorkflowInstance initial) {
        Objects.requireNonNull(initial, "initial");
        if (instances.containsKey(initial.instanceId())) {
            throw new WorkflowException(WorkflowErrorCode.INSTANCE_ALREADY_EXISTS);
        }
        WorkflowDefinition definition = loadDefinition(initial.definitionId(), initial.definitionVersion());
        WorkflowInstance canonical = WorkflowInstance.notStarted(
                initial.instanceId(), definition, initial.resourceReference(), initial.createdAt());
        if (!sameSnapshot(initial, canonical)) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        commit(initial.instanceId(), new Stored(initial, List.of()));
        return initial;
    }

    @Override
    public synchronized WorkflowInstance commitActivation(WorkflowInstance expected, WorkflowInstance activated) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(activated, "activated");
        Stored current = load(expected.instanceId());
        WorkflowInstance persisted = current.instance();
        if (persisted.runtimeVersion() != expected.runtimeVersion()
                || persisted.lifecycle() != expected.lifecycle()) {
            throw new WorkflowException(WorkflowErrorCode.VERSION_CONFLICT);
        }
        if (persisted.lifecycle() == WorkflowLifecycle.COMPLETED) {
            throw new WorkflowException(WorkflowErrorCode.INSTANCE_COMPLETED);
        }
        if (!sameSnapshot(persisted, expected) || persisted.lifecycle() != WorkflowLifecycle.NOT_STARTED
                || activated.updatedAt().isBefore(expected.updatedAt())) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        WorkflowDefinition definition = loadDefinition(persisted.definitionId(), persisted.definitionVersion());
        WorkflowInstance canonical = expected.activate(definition, activated.updatedAt());
        if (!sameSnapshot(activated, canonical)) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        commit(persisted.instanceId(), new Stored(activated, current.history()));
        return activated;
    }

    @Override
    public synchronized WorkflowInstance loadInstance(String instanceId) {
        return load(instanceId).instance();
    }

    @Override
    public synchronized List<TransitionRecord> loadHistory(String instanceId) {
        return load(instanceId).history();
    }

    @Override
    public synchronized TransitionResult commitTransition(TransitionResult transition) {
        Objects.requireNonNull(transition, "transition");
        Stored current = load(transition.instance().instanceId());
        WorkflowInstance persisted = current.instance();
        WorkflowInstance previous = transition.previousInstance();
        if (persisted.runtimeVersion() != transition.expectedVersion()) {
            throw new WorkflowException(WorkflowErrorCode.VERSION_CONFLICT);
        }
        if (persisted.lifecycle() == WorkflowLifecycle.COMPLETED) {
            throw new WorkflowException(WorkflowErrorCode.INSTANCE_COMPLETED);
        }
        if (!sameSnapshot(persisted, previous)) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        WorkflowDefinition definition = loadDefinition(persisted.definitionId(), persisted.definitionVersion());
        TransitionRecord record = transition.record();
        TransitionResult canonical = persisted.transition(definition,
                new TransitionCommand(persisted.instanceId(), record.transitionId(), record.actorReference(),
                        transition.expectedVersion()), record.occurredAt());
        if (!sameSnapshot(transition.instance(), canonical.instance()) || !record.equals(canonical.record())) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        List<TransitionRecord> nextHistory = new ArrayList<>(current.history());
        nextHistory.add(transition.record());
        nextHistory.sort(TransitionRecord.BY_RUNTIME_VERSION);
        commit(persisted.instanceId(), new Stored(transition.instance(), List.copyOf(nextHistory)));
        return transition;
    }

    private void commit(String instanceId, Stored next) {
        if (failNextCommit) {
            failNextCommit = false;
            throw new WorkflowException(WorkflowErrorCode.OPERATION_FAILURE);
        }
        instances.put(instanceId, next);
        if (loseNextCommitResponse) {
            loseNextCommitResponse = false;
            throw new WorkflowException(WorkflowErrorCode.COMMIT_OUTCOME_UNKNOWN);
        }
    }

    private static boolean sameSnapshot(WorkflowInstance left, WorkflowInstance right) {
        return left.instanceId().equals(right.instanceId())
                && left.definitionId().equals(right.definitionId())
                && left.definitionVersion() == right.definitionVersion()
                && left.resourceReference().equals(right.resourceReference())
                && left.currentState().equals(right.currentState())
                && left.runtimeVersion() == right.runtimeVersion()
                && left.lifecycle() == right.lifecycle()
                && left.createdAt().equals(right.createdAt())
                && left.updatedAt().equals(right.updatedAt());
    }

    private Stored load(String instanceId) {
        WorkflowValues.text(instanceId, "instanceId");
        Stored stored = instances.get(instanceId);
        if (stored == null) throw new WorkflowException(WorkflowErrorCode.INSTANCE_NOT_FOUND);
        return stored;
    }
}
