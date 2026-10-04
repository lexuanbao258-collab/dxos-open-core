package com.tricore.dxos.request.attachment.service;

import com.tricore.dxos.request.attachment.domain.AttachmentException;
import com.tricore.dxos.request.attachment.domain.RequestAttachment;
import com.tricore.dxos.request.attachment.dto.AttachmentResponse;
import com.tricore.dxos.request.attachment.repository.RequestAttachmentRepository;
import com.tricore.dxos.request.attachment.storage.AttachmentProperties;
import com.tricore.dxos.request.attachment.storage.AttachmentStorage;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import com.tricore.dxos.request.audit.service.AuditActorRef;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.repository.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.List;

@Service
public class RequestAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(RequestAttachmentService.class);
    private final RequestRepository requests;
    private final RequestAttachmentRepository attachments;
    private final RequestAuditService audit;
    private final AttachmentStorage storage;
    private final long maxFileSize;
    private final TransactionTemplate transaction;

    public RequestAttachmentService(RequestRepository requests, RequestAttachmentRepository attachments,
                                    RequestAuditService audit, AttachmentStorage storage,
                                    AttachmentProperties properties, PlatformTransactionManager transactionManager) {
        this.requests = requests;
        this.attachments = attachments;
        this.audit = audit;
        this.storage = storage;
        this.maxFileSize = properties.maxFileSize().toBytes();
        this.transaction = new TransactionTemplate(transactionManager);
        // Own the commit so cleanup also runs for a failure raised during database commit.
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AttachmentResponse upload(UUID requestId, MultipartFile file, String actorRef) {
        requireRequest(requestId);
        String actor = AuditActorRef.resolve(actorRef);
        validate(file);
        UUID id = UUID.randomUUID();
        String key = "requests/" + requestId + "/" + id;
        Instant uploadedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        boolean stored = false;
        try (InputStream content = file.getInputStream()) {
            storage.store(key, content);
            stored = true;
        } catch (IOException failure) {
            if (stored) cleanup(key, id);
            throw new AttachmentException(500, "ATTACHMENT_STORAGE_ERROR", "Unable to store attachment", failure);
        }
        try {
            return transaction.execute(status -> {
                requireRequest(requestId);
                RequestAttachment saved = attachments.save(new RequestAttachment(id, requestId,
                        file.getOriginalFilename(), file.getContentType(), file.getSize(), key, uploadedAt));
                audit.record(requestId, RequestAuditAction.ATTACHMENT_ADDED, actor, "attachmentId=" + id, uploadedAt);
                return AttachmentResponse.from(saved);
            });
        } catch (RuntimeException failure) {
            cleanup(key, id);
            if (failure instanceof RequestNotFoundException missing) throw missing;
            throw new AttachmentException(500, "ATTACHMENT_PERSISTENCE_ERROR", "Unable to register attachment", failure);
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(UUID requestId) {
        requireRequest(requestId);
        return attachments.findAllByRequestIdOrderByUploadedAtAscIdAsc(requestId).stream()
                .map(AttachmentResponse::from).toList();
    }

    private void requireRequest(UUID id) {
        if (!requests.existsById(id)) throw new RequestNotFoundException(id);
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) throw new AttachmentException(400, "ATTACHMENT_EMPTY", "Attachment must not be empty");
        if (file.getSize() > maxFileSize) {
            throw new AttachmentException(413, "ATTACHMENT_TOO_LARGE", "Attachment exceeds the configured size limit");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.length() > 255 || filename.equals(".")
                || filename.equals("..") || filename.contains("/") || filename.contains("\\")
                || filename.codePoints().anyMatch(Character::isISOControl)) {
            throw new AttachmentException(400, "ATTACHMENT_INVALID_FILENAME", "Attachment filename is invalid");
        }
        String contentType = file.getContentType();
        if (contentType != null && (contentType.length() > 255 || contentType.codePoints().anyMatch(Character::isISOControl))) {
            throw new AttachmentException(400, "ATTACHMENT_INVALID_CONTENT_TYPE", "Attachment content type is invalid");
        }
    }

    private void cleanup(String key, UUID attachmentId) {
        try {
            storage.delete(key);
        } catch (IOException | RuntimeException failure) {
            log.warn("Attachment cleanup failed for id {}", attachmentId);
        }
    }
}
