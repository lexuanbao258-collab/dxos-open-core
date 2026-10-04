package com.tricore.dxos.request.audit.domain;

public class InvalidAuditActorException extends RuntimeException {
    public InvalidAuditActorException() {
        super("Actor reference must be at most 100 characters and contain no control characters");
    }
}
