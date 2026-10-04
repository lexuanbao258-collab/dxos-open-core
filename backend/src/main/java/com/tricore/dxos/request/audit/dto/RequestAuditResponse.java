package com.tricore.dxos.request.audit.dto;

import com.tricore.dxos.request.audit.domain.RequestAudit;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import java.time.Instant;

public record RequestAuditResponse(RequestAuditAction action, String actorRef, String metadata, Instant createdAt) {
    public static RequestAuditResponse from(RequestAudit audit) {
        return new RequestAuditResponse(audit.getAction(), audit.getActorRef(), audit.getMetadata(), audit.getCreatedAt());
    }
}
