package com.tricore.dxos.gateway.helpdesk;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Tag(name = "IT Helpdesk MVP", description = "Quy trình IT Helpdesk local: Employee, Coordinator, Worker")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name = "dxos.helpdesk.enabled", havingValue = "true")
@RequestMapping("/api/v1/mvp/requests")
public class HelpdeskController {
    // Hibernate Validator 8 NotBlank uses String.trim(), not Unicode isBlank().
    private static final String NON_BLANK = "[^\\u0000-\\u0020]";
    private final HelpdeskService service;
    public HelpdeskController(HelpdeskService service) { this.service = service; }

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record Create(@Schema(example = "Máy tính không kết nối Internet", minLength = 1, pattern = NON_BLANK,
                                 description = "Không rỗng sau String.trim(); tối đa 200 UTF-16 code units") @NotBlank @Size(max = 200) String title,
                         @Schema(description = "Mô tả sự cố; không rỗng sau String.trim(); tối đa 4000 UTF-16 code units", minLength = 1, pattern = NON_BLANK) @NotBlank @Size(max = 4000) String description,
                         @Schema(allowableValues = "IT_HELPDESK") @NotNull @Pattern(regexp = "IT_HELPDESK") String requestType) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record Version(@Schema(description = "Phiên bản Workflow hiện tại phía client; sai phiên bản sẽ bị từ chối", example = "0", maximum = "9223372036854775807")
                          @NotNull @PositiveOrZero
                          @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedVersion) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record Assign(@Schema(description = "Canonical ID của kỹ thuật viên đủ điều kiện; không rỗng sau String.trim(); tối đa 1024 UTF-16 code units", minLength = 1, pattern = NON_BLANK)
                         @NotBlank @Size(max = 1024) String assigneeId,
                         @Schema(description = "Phiên bản phân công, tách biệt với Workflow version", example = "0", maximum = "9223372036854775807")
                         @NotNull @PositiveOrZero @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedAssignmentVersion) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record Resolve(@Schema(description = "Phiên bản Workflow hiện tại", example = "2", maximum = "9223372036854775807")
                          @NotNull @PositiveOrZero @JsonDeserialize(using = WorkflowVersionDeserializer.class) Long expectedVersion,
                          @Schema(description = "Kết quả xử lý của kỹ thuật viên; không rỗng sau String.trim(); tối đa 4000 UTF-16 code units", minLength = 1, pattern = NON_BLANK)
                          @NotBlank @Size(max = 4000) String resolution) {
        @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown field"); }
    }

    @Operation(summary = "Tạo yêu cầu IT Helpdesk", description = "Employee tạo yêu cầu, trạng thái ban đầu SUBMITTED, version 0.")
    @ApiResponse(responseCode = "201", description = "Đã tạo yêu cầu")
    @PostMapping
    public ResponseEntity<HelpdeskService.RequestView> create(@Valid @RequestBody Create input) {
        var result = service.create(input.title(), input.description());
        return ResponseEntity.created(URI.create("/api/v1/mvp/requests/" + result.id())).body(result);
    }
    @Operation(summary = "Danh sách yêu cầu được phép xem", description = "Cursor pagination theo UUID: after tùy chọn, limit từ 1 đến 100.")
    @GetMapping public HelpdeskService.Page list(@RequestParam(required = false) UUID after,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) { return service.list(after, limit); }
    @Operation(summary = "Xem chi tiết một yêu cầu theo ACL")
    @GetMapping("/{id}") public HelpdeskService.RequestView get(@PathVariable UUID id) { return service.get(id); }
    @Operation(summary = "Xem lịch sử chuyển trạng thái", description = "Assign không tạo Workflow transition riêng.")
    @GetMapping("/{id}/history") public List<HelpdeskService.HistoryView> history(@PathVariable UUID id) { return service.history(id); }
    @Operation(summary = "Coordinator tiếp nhận yêu cầu", description = "SUBMITTED → TRIAGED; yêu cầu expectedVersion.")
    @PostMapping("/{id}/triage") public HelpdeskService.RequestView triage(@PathVariable UUID id, @Valid @RequestBody Version input) {
        return service.transition(id, "triage", input.expectedVersion(), null);
    }
    @Operation(summary = "Coordinator phân công kỹ thuật viên", description = "Chỉ khi TRIAGED; sử dụng expectedAssignmentVersion, không tăng Workflow version.")
    @PostMapping("/{id}/assign") public HelpdeskService.RequestView assign(@PathVariable UUID id, @Valid @RequestBody Assign input) {
        return service.assign(id, input.assigneeId(), input.expectedAssignmentVersion());
    }
    @Operation(summary = "Worker bắt đầu xử lý", description = "TRIAGED → IN_PROGRESS; chỉ kỹ thuật viên được phân công.")
    @PostMapping("/{id}/start") public HelpdeskService.RequestView start(@PathVariable UUID id, @Valid @RequestBody Version input) {
        return service.transition(id, "start_work", input.expectedVersion(), null);
    }
    @Operation(summary = "Worker ghi nhận xử lý xong", description = "IN_PROGRESS → RESOLVED; yêu cầu resolution và expectedVersion.")
    @PostMapping("/{id}/resolve") public HelpdeskService.RequestView resolve(@PathVariable UUID id, @Valid @RequestBody Resolve input) {
        return service.transition(id, "resolve", input.expectedVersion(), input.resolution());
    }
    @Operation(summary = "Employee xác nhận hoàn tất", description = "RESOLVED → CONFIRMED; chỉ requester đủ quyền.")
    @PostMapping("/{id}/confirm") public HelpdeskService.RequestView confirm(@PathVariable UUID id, @Valid @RequestBody Version input) {
        return service.transition(id, "confirm", input.expectedVersion(), null);
    }
}
