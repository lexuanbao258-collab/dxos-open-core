package com.tricore.dxos.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "requests")
public class Request {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "request_type", nullable = false, length = 100)
    private String requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "assignee_id", length = 100)
    private String assigneeId;

    @Column(length = 4000)
    private String resolution;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Request() {
    }

    public Request(String title, String description, String requestType) {
        this.title = title;
        this.description = description;
        this.requestType = requestType;
        this.status = RequestStatus.NEW;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = createdAt;
    }

    public void assign(String assigneeId) {
        requireStatus(RequestStatus.NEW, RequestAction.ASSIGN);
        requireText(assigneeId, 100, "assigneeId");
        this.assigneeId = assigneeId;
        advanceTo(RequestStatus.ASSIGNED);
    }

    public void start() {
        requireStatus(RequestStatus.ASSIGNED, RequestAction.START);
        advanceTo(RequestStatus.IN_PROGRESS);
    }

    public void resolve(String resolution) {
        requireStatus(RequestStatus.IN_PROGRESS, RequestAction.RESOLVE);
        requireText(resolution, 4000, "resolution");
        this.resolution = resolution;
        advanceTo(RequestStatus.RESOLVED);
    }

    public void confirm() {
        requireStatus(RequestStatus.RESOLVED, RequestAction.CONFIRM);
        advanceTo(RequestStatus.CONFIRMED);
    }

    public void close() {
        requireStatus(RequestStatus.CONFIRMED, RequestAction.CLOSE);
        advanceTo(RequestStatus.CLOSED);
    }

    private void requireStatus(RequestStatus expected, RequestAction action) {
        if (status != expected) {
            throw new InvalidRequestTransitionException(status, action);
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be nonblank and at most " + maxLength + " characters");
        }
    }

    private void advanceTo(RequestStatus next) {
        status = next;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plus(1, ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getRequestType() { return requestType; }
    public RequestStatus getStatus() { return status; }
    public String getAssigneeId() { return assigneeId; }
    public String getResolution() { return resolution; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
