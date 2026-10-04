package com.tricore.dxos.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_status_history")
public class RequestStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false, length = 20)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    private RequestStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private RequestAction action;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected RequestStatusHistory() {
    }

    public RequestStatusHistory(UUID requestId, RequestStatus fromStatus, RequestStatus toStatus,
                                RequestAction action, Instant changedAt) {
        this.requestId = requestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.action = action;
        this.changedAt = changedAt;
    }

    public UUID getRequestId() { return requestId; }
    public RequestStatus getFromStatus() { return fromStatus; }
    public RequestStatus getToStatus() { return toStatus; }
    public RequestAction getAction() { return action; }
    public Instant getChangedAt() { return changedAt; }
}
