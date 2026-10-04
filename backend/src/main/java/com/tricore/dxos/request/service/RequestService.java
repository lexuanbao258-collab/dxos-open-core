package com.tricore.dxos.request.service;

import com.tricore.dxos.request.domain.Request;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.dto.CreateRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.repository.RequestRepository;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import com.tricore.dxos.request.audit.service.AuditActorRef;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RequestService {
    private final RequestRepository repository;
    private final RequestAuditService audit;

    public RequestService(RequestRepository repository, RequestAuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public RequestResponse create(CreateRequestDto input, String actorRef) {
        actorRef = AuditActorRef.resolve(actorRef);
        Request request = new Request(input.title(), input.description(), input.requestType());
        Request saved = repository.save(request);
        audit.record(saved.getId(), RequestAuditAction.REQUEST_CREATED, actorRef, null, saved.getCreatedAt());
        return RequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> list() {
        return repository.findAll().stream().map(RequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RequestResponse get(UUID id) {
        return repository.findById(id).map(RequestResponse::from)
                .orElseThrow(() -> new RequestNotFoundException(id));
    }
}
