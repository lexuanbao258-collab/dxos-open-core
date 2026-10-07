package com.tricore.dxos.core.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Contract fake only: a single map replacement models, but does not prove, database atomicity. */
final class InMemoryWorkflowPersistence implements WorkflowPersistencePort {
    private record Stored(WorkflowInstance instance, List<TransitionRecord> history) {}

    private final Map<String, Stored> instances = new HashMap<>();
    private boolean failNextCommit;

    synchronized void seed(WorkflowInstance instance) {
        if (instance.runtimeVersion() != 0 || instances.containsKey(instance.instanceId())) {
            throw new IllegalArgumentException("seed must be a new version-zero instance");
        }
        instances.put(instance.instanceId(), new Stored(instance, List.of()));
    }

    synchronized void failNextCommit() {
        failNextCommit = true;
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
        if (!persisted.definitionId().equals(previous.definitionId())
                || persisted.definitionVersion() != previous.definitionVersion()
                || !persisted.resourceReference().equals(previous.resourceReference())
                || !persisted.currentState().equals(previous.currentState())
                || persisted.lifecycle() != previous.lifecycle()
                || !persisted.createdAt().equals(previous.createdAt())
                || !persisted.updatedAt().equals(previous.updatedAt())) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        List<TransitionRecord> nextHistory = new ArrayList<>(current.history());
        nextHistory.add(transition.record());
        nextHistory.sort(TransitionRecord.BY_RUNTIME_VERSION);
        Stored next = new Stored(transition.instance(), List.copyOf(nextHistory));
        if (failNextCommit) {
            failNextCommit = false;
            throw new WorkflowException(WorkflowErrorCode.OPERATION_FAILURE);
        }
        instances.put(persisted.instanceId(), next);
        return transition;
    }

    private Stored load(String instanceId) {
        WorkflowValues.text(instanceId, "instanceId");
        Stored current = instances.get(instanceId);
        if (current == null) {
            throw new WorkflowException(WorkflowErrorCode.INSTANCE_NOT_FOUND);
        }
        return current;
    }
}
