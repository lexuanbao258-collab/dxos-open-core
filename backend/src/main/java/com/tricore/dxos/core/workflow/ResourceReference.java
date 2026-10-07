package com.tricore.dxos.core.workflow;

public record ResourceReference(String resourceType, String resourceId) {
    public ResourceReference {
        resourceType = WorkflowValues.text(resourceType, "resourceType");
        resourceId = WorkflowValues.text(resourceId, "resourceId");
    }
}
