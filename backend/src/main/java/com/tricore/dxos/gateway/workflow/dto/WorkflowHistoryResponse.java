package com.tricore.dxos.gateway.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tricore.dxos.core.workflow.TransitionRecord;

import java.time.Instant;

public record WorkflowHistoryResponse(
        String instanceId, String transitionId, String sourceState, String targetState,
        String actorId, long resultingRuntimeVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt) {
    public static WorkflowHistoryResponse from(TransitionRecord record) {
        return new WorkflowHistoryResponse(
                record.instanceId(), record.transitionId(), record.sourceState(), record.targetState(),
                record.actorReference().actorId(), record.resultingRuntimeVersion(), record.occurredAt());
    }
}
