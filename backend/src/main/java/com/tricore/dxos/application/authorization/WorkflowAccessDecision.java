package com.tricore.dxos.application.authorization;

/** Resource-policy outcomes; ALLOWED still requires the operation authority. */
public enum WorkflowAccessDecision {
    ALLOWED,
    FORBIDDEN,
    HIDDEN,
    UNAVAILABLE
}
