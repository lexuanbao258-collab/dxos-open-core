package com.tricore.dxos.core.workflow;

import java.util.Objects;

/** Validated transition proposal; success becomes durable only after the port commits it. */
public final class TransitionResult {
    private final WorkflowInstance previousInstance;
    private final WorkflowInstance instance;
    private final TransitionRecord record;

    TransitionResult(WorkflowInstance previousInstance, WorkflowInstance instance, TransitionRecord record) {
        this.previousInstance = Objects.requireNonNull(previousInstance, "previousInstance");
        this.instance = Objects.requireNonNull(instance, "instance");
        this.record = Objects.requireNonNull(record, "record");
        if (previousInstance.lifecycle() != WorkflowLifecycle.ACTIVE
                || instance.lifecycle() == WorkflowLifecycle.NOT_STARTED
                || previousInstance.runtimeVersion() == Long.MAX_VALUE
                || instance.runtimeVersion() != previousInstance.runtimeVersion() + 1
                || !previousInstance.instanceId().equals(instance.instanceId())
                || !previousInstance.definitionId().equals(instance.definitionId())
                || previousInstance.definitionVersion() != instance.definitionVersion()
                || !previousInstance.resourceReference().equals(instance.resourceReference())
                || !previousInstance.createdAt().equals(instance.createdAt())
                || instance.updatedAt().isBefore(previousInstance.updatedAt())
                || !record.instanceId().equals(instance.instanceId())
                || !record.sourceState().equals(previousInstance.currentState())
                || !record.targetState().equals(instance.currentState())
                || record.resultingRuntimeVersion() != instance.runtimeVersion()
                || !record.occurredAt().equals(instance.updatedAt())) {
            throw new IllegalArgumentException("transition snapshots and history must agree");
        }
    }

    public WorkflowInstance previousInstance() { return previousInstance; }
    public WorkflowInstance instance() { return instance; }
    public TransitionRecord record() { return record; }
    public long expectedVersion() { return previousInstance.runtimeVersion(); }
}
