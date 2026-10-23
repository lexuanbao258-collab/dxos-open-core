package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.dto.AssignRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.dto.ResolveRequestDto;
import com.tricore.dxos.request.dto.RequestHistoryResponse;
import com.tricore.dxos.request.service.RequestWorkflowService;
import com.tricore.dxos.common.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/requests/{id}")
@Tag(name = "Request")
public class RequestWorkflowController {
    private final RequestWorkflowService service;

    public RequestWorkflowController(RequestWorkflowService service) {
        this.service = service;
    }

    @PostMapping("/assign")
    @Operation(summary = "Assign a NEW Request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID, input or JSON", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Request not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Invalid transition or concurrent update", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public RequestResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignRequestDto input) {
        return service.assign(id, input.assigneeId());
    }

    @PostMapping("/start")
    @Operation(summary = "Start an ASSIGNED Request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Request not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Invalid transition or concurrent update", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public RequestResponse start(@PathVariable UUID id) {
        return service.start(id);
    }

    @PostMapping("/resolve")
    @Operation(summary = "Resolve an IN_PROGRESS Request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID, input or JSON", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Request not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Invalid transition or concurrent update", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public RequestResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequestDto input) {
        return service.resolve(id, input.resolution());
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm a RESOLVED Request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Request not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Invalid transition or concurrent update", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public RequestResponse confirm(@PathVariable UUID id) {
        return service.confirm(id);
    }

    @PostMapping("/close")
    @Operation(summary = "Close a CONFIRMED Request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Request not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Invalid transition or concurrent update", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public RequestResponse close(@PathVariable UUID id) {
        return service.close(id);
    }

    @GetMapping("/history")
    @Operation(summary = "Get Request status history", description = "Ordered by changedAt then internal history id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RequestHistoryResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Request not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public List<RequestHistoryResponse> history(@PathVariable UUID id) {
        return service.history(id);
    }
}
