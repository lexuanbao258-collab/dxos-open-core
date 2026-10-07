package com.tricore.dxos.core.workflow;

import java.util.Objects;

public record TransitionCommand(String instanceId, String transitionId, ActorReference actorReference,
                                long expectedVersion) {
    public TransitionCommand {
        instanceId = WorkflowValues.text(instanceId, "instanceId");
        transitionId = WorkflowValues.text(transitionId, "transitionId");
        Objects.requireNonNull(actorReference, "actorReference");
        WorkflowValues.nonNegative(expectedVersion, "expectedVersion");
    }
}
