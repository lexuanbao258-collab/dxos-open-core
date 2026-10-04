package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.audit.dto.RequestAuditResponse;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests/{id}/audit")
public class RequestAuditController {
    private final RequestAuditService service;

    public RequestAuditController(RequestAuditService service) {
        this.service = service;
    }

    @GetMapping
    public List<RequestAuditResponse> list(@PathVariable UUID id) {
        return service.list(id);
    }
}
