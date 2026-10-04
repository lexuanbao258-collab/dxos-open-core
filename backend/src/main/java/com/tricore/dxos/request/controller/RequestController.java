package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.dto.CreateRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.service.RequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {
    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequestDto input) {
        RequestResponse response = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/requests/" + response.id())).body(response);
    }
}
