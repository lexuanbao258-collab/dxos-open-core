package com.tricore.dxos.gateway.helpdesk;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.tricore.dxos.gateway.workflow.dto.WorkflowVersionDeserializer;
import com.tricore.dxos.infrastructure.workflow.HelpdeskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "dxos.helpdesk.enabled", havingValue = "true")
@RequestMapping("/api/v1/mvp/requests")
public class HelpdeskController {
    private final HelpdeskService service;
    public HelpdeskController(HelpdeskService service) { this.service = service; }

    public record Create(@NotBlank @Size(max = 200) String title,
                         @NotBlank @Size(max = 4000) String description,
                         @NotNull @Pattern(regexp = "IT_HELPDESK") String requestType) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }
    public record Version(@NotNull @PositiveOrZero
                          @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedVersion) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }
    public record Assign(@NotBlank @Size(max = 1024) String assigneeId,
                         @NotNull @PositiveOrZero @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedAssignmentVersion) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }
    public record Resolve(@NotNull @PositiveOrZero @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedVersion,
                          @NotBlank @Size(max = 4000) String resolution) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }

    @PostMapping
    public ResponseEntity<HelpdeskService.RequestView> create(@Valid @RequestBody Create input) {
        var result = service.create(input.title(), input.description());
        return ResponseEntity.created(URI.create("/api/v1/mvp/requests/" + result.id())).body(result);
    }
    @GetMapping public HelpdeskService.Page list(@RequestParam(required = false) UUID after,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) { return service.list(after, limit); }
    @GetMapping("/{id}") public HelpdeskService.RequestView get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/history") public List<HelpdeskService.HistoryView> history(@PathVariable UUID id) { return service.history(id); }
    @PostMapping("/{id}/triage") public HelpdeskService.RequestView triage(@PathVariable UUID id, @Valid @RequestBody Version input) {
        return service.transition(id, "triage", input.expectedVersion(), null);
    }
    @PostMapping("/{id}/assign") public HelpdeskService.RequestView assign(@PathVariable UUID id, @Valid @RequestBody Assign input) {
        return service.assign(id, input.assigneeId(), input.expectedAssignmentVersion());
    }
    @PostMapping("/{id}/start") public HelpdeskService.RequestView start(@PathVariable UUID id, @Valid @RequestBody Version input) {
        return service.transition(id, "start_work", input.expectedVersion(), null);
    }
    @PostMapping("/{id}/resolve") public HelpdeskService.RequestView resolve(@PathVariable UUID id, @Valid @RequestBody Resolve input) {
        return service.transition(id, "resolve", input.expectedVersion(), input.resolution());
    }
    @PostMapping("/{id}/confirm") public HelpdeskService.RequestView confirm(@PathVariable UUID id, @Valid @RequestBody Version input) {
        return service.transition(id, "confirm", input.expectedVersion(), null);
    }
}
