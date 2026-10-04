package com.tricore.dxos.request.audit.service;

import com.tricore.dxos.request.audit.domain.RequestAudit;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import com.tricore.dxos.request.audit.repository.RequestAuditRepository;
import com.tricore.dxos.request.audit.dto.RequestAuditResponse;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.repository.RequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Service
public class RequestAuditService {
    private final RequestAuditRepository repository;
    private final RequestRepository requests;

    public RequestAuditService(RequestAuditRepository repository, RequestRepository requests) {
        this.repository = repository;
        this.requests = requests;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID requestId, RequestAuditAction action, String actorRef, String metadata, Instant createdAt) {
        repository.save(new RequestAudit(requestId, action, AuditActorRef.resolve(actorRef), metadata, createdAt));
    }

    @Transactional(readOnly = true)
    public List<RequestAuditResponse> list(UUID requestId) {
        if (!requests.existsById(requestId)) {
            throw new RequestNotFoundException(requestId);
        }
        return repository.findAllByRequestIdOrderByCreatedAtAscIdAsc(requestId).stream()
                .map(RequestAuditResponse::from).toList();
    }
}
