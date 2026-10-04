package com.tricore.dxos.request.attachment.repository;

import com.tricore.dxos.request.attachment.domain.RequestAttachment;
import org.springframework.data.repository.Repository;
import java.util.List;
import java.util.UUID;

public interface RequestAttachmentRepository extends Repository<RequestAttachment, UUID> {
    RequestAttachment save(RequestAttachment attachment);
    List<RequestAttachment> findAllByRequestIdOrderByUploadedAtAscIdAsc(UUID requestId);
}
