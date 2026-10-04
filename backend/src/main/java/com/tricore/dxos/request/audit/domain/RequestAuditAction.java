package com.tricore.dxos.request.audit.domain;

import com.tricore.dxos.request.domain.RequestAction;

public enum RequestAuditAction {
    REQUEST_CREATED, REQUEST_ASSIGNED, REQUEST_STARTED, REQUEST_RESOLVED,
    REQUEST_CONFIRMED, REQUEST_CLOSED, ATTACHMENT_ADDED;

    public static RequestAuditAction forWorkflow(RequestAction action) {
        return switch (action) {
            case ASSIGN -> REQUEST_ASSIGNED;
            case START -> REQUEST_STARTED;
            case RESOLVE -> REQUEST_RESOLVED;
            case CONFIRM -> REQUEST_CONFIRMED;
            case CLOSE -> REQUEST_CLOSED;
        };
    }
}
