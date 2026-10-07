package com.tricore.dxos.core.workflow;

/** Trusted opaque reference supplied upstream; this type does not authenticate an actor. */
public record ActorReference(String actorId) {
    public ActorReference {
        actorId = WorkflowValues.text(actorId, "actorId");
    }
}
