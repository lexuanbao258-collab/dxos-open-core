package com.tricore.dxos.request.audit.service;

import com.tricore.dxos.request.audit.domain.InvalidAuditActorException;

public final class AuditActorRef {
    private AuditActorRef() {
    }

    public static String resolve(String suppliedActor) {
        if (suppliedActor == null || suppliedActor.isBlank()) return "anonymous";
        if (suppliedActor.length() > 100 || suppliedActor.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidAuditActorException();
        }
        return suppliedActor.strip();
    }
}
