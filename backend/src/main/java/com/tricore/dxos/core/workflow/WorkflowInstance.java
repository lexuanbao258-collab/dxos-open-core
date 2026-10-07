package com.tricore.dxos.core.workflow;

import java.time.Instant;
import java.util.Objects;

/** Immutable runtime snapshot. State changes are derived from a pinned definition, never setters. */
public final class WorkflowInstance {
    private final String instanceId;
    private final String definitionId;
    private final long definitionVersion;
    private final ResourceReference resourceReference;
    private final String currentState;
    private final long runtimeVersion;
    private final WorkflowLifecycle lifecycle;
    private final Instant createdAt;
    private final Instant updatedAt;

    private WorkflowInstance(String instanceId, WorkflowDefinition definition, ResourceReference resourceReference,
                             String currentState, long runtimeVersion, WorkflowLifecycle lifecycle,
                             Instant createdAt, Instant updatedAt) {
        this.instanceId = WorkflowValues.text(instanceId, "instanceId");
        this.definitionId = definition.definitionId();
        this.definitionVersion = definition.definitionVersion();
        this.resourceReference = Objects.requireNonNull(resourceReference, "resourceReference");
        this.currentState = WorkflowValues.text(currentState, "currentState");
        this.runtimeVersion = WorkflowValues.nonNegative(runtimeVersion, "runtimeVersion");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
        boolean terminal = definition.isTerminal(currentState);
        if (lifecycle == WorkflowLifecycle.NOT_STARTED) {
            if (runtimeVersion != 0 || !currentState.equals(definition.initialState()) || !createdAt.equals(updatedAt)) {
                throw new IllegalArgumentException("not-started snapshot must be initial and version zero");
            }
        } else if ((lifecycle == WorkflowLifecycle.COMPLETED) != terminal) {
            throw new IllegalArgumentException("lifecycle must agree with terminal state");
        }
        if (runtimeVersion == 0 && !currentState.equals(definition.initialState())) {
            throw new IllegalArgumentException("version zero must use initial state");
        }
    }

    public static WorkflowInstance notStarted(String instanceId, WorkflowDefinition definition,
                                              ResourceReference resourceReference, Instant createdAt) {
        Objects.requireNonNull(definition, "definition");
        return new WorkflowInstance(instanceId, definition, resourceReference, definition.initialState(), 0,
                WorkflowLifecycle.NOT_STARTED, createdAt, createdAt);
    }

    /** Adapter reconstitution only, not a command to overwrite persisted state. */
    public static WorkflowInstance restore(String instanceId, WorkflowDefinition exactDefinition,
                                           ResourceReference resourceReference, String currentState,
                                           long runtimeVersion, WorkflowLifecycle lifecycle,
                                           Instant createdAt, Instant updatedAt) {
        return new WorkflowInstance(instanceId, Objects.requireNonNull(exactDefinition, "exactDefinition"),
                resourceReference, currentState, runtimeVersion, lifecycle, createdAt, updatedAt);
    }

    /** Initialization does not count as a transition. Its persistence is outside the transition port. */
    public WorkflowInstance activate(WorkflowDefinition exactDefinition, Instant startedAt) {
        requireDefinition(exactDefinition);
        requireTime(startedAt);
        if (lifecycle != WorkflowLifecycle.NOT_STARTED) {
            throw new WorkflowException(lifecycle == WorkflowLifecycle.COMPLETED
                    ? WorkflowErrorCode.INSTANCE_COMPLETED : WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        if (exactDefinition.isTerminal(currentState)) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        return new WorkflowInstance(instanceId, exactDefinition, resourceReference, currentState, 0,
                WorkflowLifecycle.ACTIVE, createdAt, startedAt);
    }

    public TransitionResult transition(WorkflowDefinition exactDefinition, TransitionCommand command, Instant occurredAt) {
        Objects.requireNonNull(command, "command");
        requireDefinition(exactDefinition);
        if (!instanceId.equals(command.instanceId())) {
            throw new WorkflowException(WorkflowErrorCode.INSTANCE_NOT_FOUND);
        }
        if (runtimeVersion != command.expectedVersion()) {
            throw new WorkflowException(WorkflowErrorCode.VERSION_CONFLICT);
        }
        if (lifecycle == WorkflowLifecycle.COMPLETED) {
            throw new WorkflowException(WorkflowErrorCode.INSTANCE_COMPLETED);
        }
        if (lifecycle != WorkflowLifecycle.ACTIVE) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        WorkflowTransition transition = exactDefinition.transition(command.transitionId());
        if (!currentState.equals(transition.sourceState())) {
            throw new WorkflowException(WorkflowErrorCode.TRANSITION_NOT_ALLOWED);
        }
        requireTime(occurredAt);
        if (runtimeVersion == Long.MAX_VALUE) {
            throw new WorkflowException(WorkflowErrorCode.OPERATION_FAILURE);
        }
        long nextVersion = runtimeVersion + 1;
        WorkflowLifecycle nextLifecycle = exactDefinition.isTerminal(transition.targetState())
                ? WorkflowLifecycle.COMPLETED : WorkflowLifecycle.ACTIVE;
        WorkflowInstance next = new WorkflowInstance(instanceId, exactDefinition, resourceReference,
                transition.targetState(), nextVersion, nextLifecycle, createdAt, occurredAt);
        TransitionRecord record = new TransitionRecord(instanceId, transition.transitionId(), currentState,
                next.currentState(), command.actorReference(), nextVersion, occurredAt);
        return new TransitionResult(this, next, record);
    }

    private void requireDefinition(WorkflowDefinition definition) {
        if (definition == null || !definitionId.equals(definition.definitionId())
                || definitionVersion != definition.definitionVersion()) {
            throw new WorkflowException(WorkflowErrorCode.DEFINITION_NOT_FOUND);
        }
    }

    private void requireTime(Instant time) {
        if (Objects.requireNonNull(time, "time").isBefore(updatedAt)) {
            throw new IllegalArgumentException("operation timestamp must not move backwards");
        }
    }

    public String instanceId() { return instanceId; }
    public String definitionId() { return definitionId; }
    public long definitionVersion() { return definitionVersion; }
    public ResourceReference resourceReference() { return resourceReference; }
    public String currentState() { return currentState; }
    public long runtimeVersion() { return runtimeVersion; }
    public WorkflowLifecycle lifecycle() { return lifecycle; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
