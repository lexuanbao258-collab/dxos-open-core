package com.tricore.dxos.core.workflow;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

/** Successful transitions only. Versions, rather than timestamps, define per-instance order. */
public record TransitionRecord(String instanceId, String transitionId, String sourceState,
                               String targetState, ActorReference actorReference,
                               long resultingRuntimeVersion, Instant occurredAt) {
    public static final Comparator<TransitionRecord> BY_RUNTIME_VERSION =
            Comparator.comparingLong(TransitionRecord::resultingRuntimeVersion);

    public TransitionRecord {
        instanceId = WorkflowValues.text(instanceId, "instanceId");
        transitionId = WorkflowValues.text(transitionId, "transitionId");
        sourceState = WorkflowValues.text(sourceState, "sourceState");
        targetState = WorkflowValues.text(targetState, "targetState");
        Objects.requireNonNull(actorReference, "actorReference");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (resultingRuntimeVersion <= 0) {
            throw new IllegalArgumentException("resultingRuntimeVersion must be positive");
        }
    }
}
