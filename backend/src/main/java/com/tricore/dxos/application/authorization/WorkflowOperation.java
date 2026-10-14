package com.tricore.dxos.application.authorization;

import com.tricore.dxos.core.identity.RequiredAuthority;

public enum WorkflowOperation {
    CREATE("workflow:create"),
    ACTIVATE("workflow:activate"),
    TRANSITION("workflow:transition"),
    READ("workflow:read");

    private final RequiredAuthority requiredAuthority;

    WorkflowOperation(String authority) {
        requiredAuthority = new RequiredAuthority(authority);
    }

    public RequiredAuthority requiredAuthority() {
        return requiredAuthority;
    }
}
