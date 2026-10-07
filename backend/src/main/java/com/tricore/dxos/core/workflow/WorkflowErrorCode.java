package com.tricore.dxos.core.workflow;

/** Stable capability errors; transport mappings and provider causes belong outside Core. */
public enum WorkflowErrorCode {
    DEFINITION_NOT_FOUND,
    INSTANCE_NOT_FOUND,
    INVALID_TRANSITION,
    TRANSITION_NOT_ALLOWED,
    INSTANCE_COMPLETED,
    VERSION_CONFLICT,
    OPERATION_FAILURE
}
