package com.tricore.dxos.request.service;

import com.tricore.dxos.request.domain.Request;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.dto.CreateRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.repository.RequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RequestService {
    private final RequestRepository repository;

    public RequestService(RequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RequestResponse create(CreateRequestDto input) {
        Request request = new Request(input.title(), input.description(), input.requestType());
        return RequestResponse.from(repository.save(request));
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
