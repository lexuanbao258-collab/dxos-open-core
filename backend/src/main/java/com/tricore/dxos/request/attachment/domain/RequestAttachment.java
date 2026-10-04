package com.tricore.dxos.request.attachment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_attachments")
public class RequestAttachment {
    @Id private UUID id;
    @Column(name = "request_id", nullable = false, updatable = false) private UUID requestId;
    @Column(name = "original_filename", nullable = false, length = 255, updatable = false) private String originalFilename;
    @Column(name = "content_type", length = 255, updatable = false) private String contentType;
    @Column(nullable = false, updatable = false) private long size;
    @Column(name = "storage_key", nullable = false, length = 120, updatable = false) private String storageKey;
    @Column(name = "uploaded_at", nullable = false, updatable = false) private Instant uploadedAt;

    protected RequestAttachment() { }

    public RequestAttachment(UUID id, UUID requestId, String originalFilename, String contentType,
                             long size, String storageKey, Instant uploadedAt) {
        this.id = id;
        this.requestId = requestId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
        this.storageKey = storageKey;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public String getStorageKey() { return storageKey; }
    public Instant getUploadedAt() { return uploadedAt; }
}
