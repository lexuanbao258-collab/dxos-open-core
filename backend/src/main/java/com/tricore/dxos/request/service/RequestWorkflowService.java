package com.tricore.dxos.request.service;

import com.tricore.dxos.request.domain.Request;
import com.tricore.dxos.request.domain.RequestAction;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.domain.RequestStatusHistory;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.repository.RequestRepository;
import com.tricore.dxos.request.repository.RequestStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RequestWorkflowService {
    private final RequestRepository requests;
    private final RequestStatusHistoryRepository history;

    public RequestWorkflowService(RequestRepository requests, RequestStatusHistoryRepository history) {
        this.requests = requests;
        this.history = history;
    }

    @Transactional
    public RequestResponse assign(UUID id, String assigneeId) {
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.assign(assigneeId);
        return persistTransition(request, from, RequestAction.ASSIGN);
    }

    @Transactional
    public RequestResponse start(UUID id) {
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.start();
        return persistTransition(request, from, RequestAction.START);
    }

    @Transactional
    public RequestResponse resolve(UUID id, String resolution) {
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.resolve(resolution);
        return persistTransition(request, from, RequestAction.RESOLVE);
    }

    @Transactional
    public RequestResponse confirm(UUID id) {
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.confirm();
        return persistTransition(request, from, RequestAction.CONFIRM);
    }

    @Transactional
    public RequestResponse close(UUID id) {
        Request request = load(id);
        RequestStatus from = request.getStatus();
        request.close();
        return persistTransition(request, from, RequestAction.CLOSE);
    }

    private Request load(UUID id) {
        return requests.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
    }

    private RequestResponse persistTransition(Request request, RequestStatus from, RequestAction action) {
        // Flush checks the optimistic version and obtains its new value before mapping the response.
        Request saved = requests.saveAndFlush(request);
        history.save(new RequestStatusHistory(saved.getId(), from, saved.getStatus(), action, saved.getUpdatedAt()));
        return RequestResponse.from(saved);
    }
}
