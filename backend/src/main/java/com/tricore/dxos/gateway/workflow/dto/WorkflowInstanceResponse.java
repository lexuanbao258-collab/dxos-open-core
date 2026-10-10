package com.tricore.dxos.gateway.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tricore.dxos.core.workflow.WorkflowInstance;
import com.tricore.dxos.core.workflow.WorkflowLifecycle;

import java.time.Instant;

public record WorkflowInstanceResponse(
        String instanceId, String definitionId, long definitionVersion,
        String resourceType, String resourceId, String currentState,
        long runtimeVersion, WorkflowLifecycle lifecycle,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt) {
    public static WorkflowInstanceResponse from(WorkflowInstance instance) {
        return new WorkflowInstanceResponse(
                instance.instanceId(), instance.definitionId(), instance.definitionVersion(),
                instance.resourceReference().resourceType(), instance.resourceReference().resourceId(),
                instance.currentState(), instance.runtimeVersion(), instance.lifecycle(),
                instance.createdAt(), instance.updatedAt());
    }
}
