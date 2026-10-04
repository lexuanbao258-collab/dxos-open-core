package com.tricore.dxos.request.audit.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "request_audit")
public class RequestAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private RequestAuditAction action;

    @Column(name = "actor_ref", nullable = false, updatable = false, length = 100)
    private String actorRef;

    @Column(updatable = false, columnDefinition = "text")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RequestAudit() {
    }

    public RequestAudit(UUID requestId, RequestAuditAction action, String actorRef, String metadata, Instant createdAt) {
        this.requestId = requestId;
        this.action = action;
        this.actorRef = actorRef;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public RequestAuditAction getAction() { return action; }
    public String getActorRef() { return actorRef; }
    public String getMetadata() { return metadata; }
    public Instant getCreatedAt() { return createdAt; }
}
