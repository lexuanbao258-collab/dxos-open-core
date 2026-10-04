package com.tricore.dxos.request.attachment.dto;

import com.tricore.dxos.request.attachment.domain.RequestAttachment;
import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(UUID id, UUID requestId, String originalFilename, String contentType,
                                 long size, Instant uploadedAt) {
    public static AttachmentResponse from(RequestAttachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getRequestId(), attachment.getOriginalFilename(),
                attachment.getContentType(), attachment.getSize(), attachment.getUploadedAt());
    }
}
