package com.tricore.dxos.request.dto;

import com.tricore.dxos.request.domain.RequestAction;
import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.domain.RequestStatusHistory;

import java.time.Instant;

public record RequestHistoryResponse(RequestStatus fromStatus, RequestStatus toStatus,
                                     RequestAction action, Instant changedAt) {
    public static RequestHistoryResponse from(RequestStatusHistory entry) {
        return new RequestHistoryResponse(entry.getFromStatus(), entry.getToStatus(),
                entry.getAction(), entry.getChangedAt());
    }
}
