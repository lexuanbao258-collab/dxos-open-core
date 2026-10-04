package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.dto.AssignRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.dto.ResolveRequestDto;
import com.tricore.dxos.request.dto.RequestHistoryResponse;
import com.tricore.dxos.request.service.RequestWorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/requests/{id}")
public class RequestWorkflowController {
    private final RequestWorkflowService service;

    public RequestWorkflowController(RequestWorkflowService service) {
        this.service = service;
    }

    @PostMapping("/assign")
    public RequestResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignRequestDto input,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "anonymous") String actorRef) {
        return service.assign(id, input.assigneeId(), actorRef);
    }

    @PostMapping("/start")
    public RequestResponse start(@PathVariable UUID id,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "anonymous") String actorRef) {
        return service.start(id, actorRef);
    }

    @PostMapping("/resolve")
    public RequestResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequestDto input,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "anonymous") String actorRef) {
        return service.resolve(id, input.resolution(), actorRef);
    }

    @PostMapping("/confirm")
    public RequestResponse confirm(@PathVariable UUID id,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "anonymous") String actorRef) {
        return service.confirm(id, actorRef);
    }

    @PostMapping("/close")
    public RequestResponse close(@PathVariable UUID id,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "anonymous") String actorRef) {
        return service.close(id, actorRef);
    }

    @GetMapping("/history")
    public List<RequestHistoryResponse> history(@PathVariable UUID id) {
        return service.history(id);
    }
}
