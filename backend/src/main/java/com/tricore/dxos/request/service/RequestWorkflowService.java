package com.tricore.dxos.request.service;

import com.tricore.dxos.request.domain.Request;
import com.tricore.dxos.request.domain.RequestAction;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.domain.RequestStatusHistory;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.dto.RequestHistoryResponse;
import com.tricore.dxos.request.repository.RequestRepository;
import com.tricore.dxos.request.repository.RequestStatusHistoryRepository;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import com.tricore.dxos.request.audit.service.AuditActorRef;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
public class RequestWorkflowService {
    private final RequestRepository requests;
    private final RequestStatusHistoryRepository history;
    private final RequestAuditService audit;

    public RequestWorkflowService(RequestRepository requests, RequestStatusHistoryRepository history, RequestAuditService audit) {
        this.requests = requests;
        this.history = history;
        this.audit = audit;
    }

    @Transactional
    public RequestResponse assign(UUID id, String assigneeId, String actorRef) {
        actorRef = AuditActorRef.resolve(actorRef);
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.assign(assigneeId);
        return persistTransition(request, from, RequestAction.ASSIGN, actorRef);
    }

    @Transactional
    public RequestResponse start(UUID id, String actorRef) {
        actorRef = AuditActorRef.resolve(actorRef);
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.start();
        return persistTransition(request, from, RequestAction.START, actorRef);
    }

    @Transactional
    public RequestResponse resolve(UUID id, String resolution, String actorRef) {
        actorRef = AuditActorRef.resolve(actorRef);
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.resolve(resolution);
        return persistTransition(request, from, RequestAction.RESOLVE, actorRef);
    }

    @Transactional
    public RequestResponse confirm(UUID id, String actorRef) {
        actorRef = AuditActorRef.resolve(actorRef);
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.confirm();
        return persistTransition(request, from, RequestAction.CONFIRM, actorRef);
    }

    @Transactional
    public RequestResponse close(UUID id, String actorRef) {
        actorRef = AuditActorRef.resolve(actorRef);
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.close();
        return persistTransition(request, from, RequestAction.CLOSE, actorRef);
    }

    @Transactional(readOnly = true)
    public List<RequestHistoryResponse> history(UUID id) {
        load(id);
        return history.findAllByRequestIdOrderByChangedAtAscIdAsc(id).stream()
                .map(RequestHistoryResponse::from).toList();
    }

    private Request load(UUID id) {
        return requests.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
    }

    private RequestResponse persistTransition(Request request, RequestStatus from, RequestAction action, String actorRef) {
        // Flush checks the optimistic version and obtains its new value before mapping the response.
        Request saved = requests.saveAndFlush(request);
        history.save(new RequestStatusHistory(saved.getId(), from, saved.getStatus(), action, saved.getUpdatedAt()));
        audit.record(saved.getId(), RequestAuditAction.forWorkflow(action), actorRef, null, saved.getUpdatedAt());
        return RequestResponse.from(saved);
    }
}
