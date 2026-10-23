package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.dto.CreateRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.service.RequestService;
import com.tricore.dxos.common.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
@Tag(name = "Request")
public class RequestController {
    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a Request", description = "Creates a NEW Request. Unknown body fields are rejected. The prototype has no tenant or JWT enforcement.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; Location identifies the Request", content = @Content(schema = @Schema(implementation = RequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or JSON", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequestDto input) {
        RequestResponse response = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/requests/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "List Requests", description = "Returns an unpaged list with no guaranteed order.")
    public List<RequestResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a Request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Request not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    public RequestResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
