package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.dto.AssignRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.dto.ResolveRequestDto;
import com.tricore.dxos.request.service.RequestWorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests/{id}")
public class RequestWorkflowController {
    private final RequestWorkflowService service;

    public RequestWorkflowController(RequestWorkflowService service) {
        this.service = service;
    }

    @PostMapping("/assign")
    public RequestResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignRequestDto input) {
        return service.assign(id, input.assigneeId());
    }

    @PostMapping("/start")
    public RequestResponse start(@PathVariable UUID id) {
        return service.start(id);
    }

    @PostMapping("/resolve")
    public RequestResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequestDto input) {
        return service.resolve(id, input.resolution());
    }

    @PostMapping("/confirm")
    public RequestResponse confirm(@PathVariable UUID id) {
        return service.confirm(id);
    }

    @PostMapping("/close")
    public RequestResponse close(@PathVariable UUID id) {
        return service.close(id);
    }
}
