package com.tricore.dxos.core.workflow;

public record WorkflowTransition(String transitionId, String sourceState, String targetState) {
    public WorkflowTransition {
        transitionId = WorkflowValues.text(transitionId, "transitionId");
        sourceState = WorkflowValues.text(sourceState, "sourceState");
        targetState = WorkflowValues.text(targetState, "targetState");
    }
}
