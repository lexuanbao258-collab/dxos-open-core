package com.tricore.dxos.request.dto;

import com.tricore.dxos.request.domain.Request;
import com.tricore.dxos.request.domain.RequestStatus;

import java.time.Instant;
import java.util.UUID;

public record RequestResponse(UUID id, String title, String description, String requestType,
                              RequestStatus status, String assigneeId, String resolution, Long version,
                              Instant createdAt, Instant updatedAt) {
    public static RequestResponse from(Request request) {
        return new RequestResponse(request.getId(), request.getTitle(), request.getDescription(),
                request.getRequestType(), request.getStatus(), request.getAssigneeId(), request.getResolution(),
                request.getVersion(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
