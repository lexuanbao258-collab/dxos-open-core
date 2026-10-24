package com.tricore.dxos.application.mvp;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * IT Helpdesk 9-route HTTP adapter, deliberately NOT a Spring component.
 * Registration is BLOCKED until a real secured MvpRequestOperations implementation,
 * authoritative request/workflow binding and write-boundary guards are independently tested.
 * In particular, DO NOT add @RestController or instantiate this as a Spring bean merely
 * to make routes reachable. Existing /api/v1/requests/** is an unscoped prototype.
 */
@RequestMapping("/api/v1/mvp/requests")
@ResponseBody
public final class MvpRequestController {
    private final MvpRequestOperations operations;

    public MvpRequestController(MvpRequestOperations operations) {
        this.operations = Objects.requireNonNull(operations, "guarded operations");
    }

    @PostMapping
    public ResponseEntity<MvpRequestDtos.Detail> create(@Valid @RequestBody MvpRequestDtos.Create input) {
        MvpRequestDtos.Detail created = operations.create(input);
        return ResponseEntity.created(URI.create("/api/v1/mvp/requests/" + created.id())).body(created);
    }

    @GetMapping
    public List<MvpRequestDtos.Summary> list() {
        return operations.listVisible();
    }

    @GetMapping("/{id}")
    public MvpRequestDtos.Detail detail(@PathVariable UUID id) {
        return operations.getVisible(id);
    }

    @PostMapping("/{id}/triage")
    public MvpRequestDtos.Detail triage(@PathVariable UUID id,
                                        @Valid @RequestBody MvpRequestDtos.VersionedAction input) {
        return operations.triage(id, input);
    }

    @PostMapping("/{id}/assign")
    public MvpRequestDtos.Detail assign(@PathVariable UUID id,
                                        @Valid @RequestBody MvpRequestDtos.Assign input) {
        return operations.assign(id, input);
    }

    @PostMapping("/{id}/start")
    public MvpRequestDtos.Detail start(@PathVariable UUID id,
                                       @Valid @RequestBody MvpRequestDtos.VersionedAction input) {
        return operations.start(id, input);
    }

    @PostMapping("/{id}/resolve")
    public MvpRequestDtos.Detail resolve(@PathVariable UUID id,
                                         @Valid @RequestBody MvpRequestDtos.Resolve input) {
        return operations.resolve(id, input);
    }

    @PostMapping("/{id}/confirm")
    public MvpRequestDtos.Detail confirm(@PathVariable UUID id,
                                         @Valid @RequestBody MvpRequestDtos.VersionedAction input) {
        return operations.confirm(id, input);
    }

    @GetMapping("/{id}/history")
    public List<MvpRequestDtos.HistoryEvent> history(@PathVariable UUID id) {
        return operations.historyVisible(id);
    }
}
