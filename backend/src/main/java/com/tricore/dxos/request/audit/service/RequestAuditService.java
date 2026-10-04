package com.tricore.dxos.request.audit.service;

import com.tricore.dxos.request.audit.domain.RequestAudit;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import com.tricore.dxos.request.audit.repository.RequestAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RequestAuditService {
    private final RequestAuditRepository repository;

    public RequestAuditService(RequestAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID requestId, RequestAuditAction action, String actorRef, String metadata, Instant createdAt) {
        repository.save(new RequestAudit(requestId, action, AuditActorRef.resolve(actorRef), metadata, createdAt));
    }
}
