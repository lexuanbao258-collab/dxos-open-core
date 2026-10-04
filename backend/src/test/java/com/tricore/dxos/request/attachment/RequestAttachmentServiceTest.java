package com.tricore.dxos.request.attachment;

import com.tricore.dxos.request.attachment.domain.AttachmentException;
import com.tricore.dxos.request.attachment.domain.RequestAttachment;
import com.tricore.dxos.request.attachment.repository.RequestAttachmentRepository;
import com.tricore.dxos.request.attachment.service.RequestAttachmentService;
import com.tricore.dxos.request.attachment.storage.AttachmentProperties;
import com.tricore.dxos.request.attachment.storage.AttachmentStorage;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.repository.RequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.unit.DataSize;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestAttachmentServiceTest {
    @Mock RequestRepository requests;
    @Mock RequestAttachmentRepository attachments;
    @Mock RequestAuditService audit;
    @Mock AttachmentStorage storage;
    @Mock PlatformTransactionManager manager;
    @Mock TransactionStatus transaction;
    RequestAttachmentService service;
    final UUID requestId = UUID.randomUUID();
    final MockMultipartFile file = new MockMultipartFile("file", "diagnostic.txt", "text/plain", new byte[]{1, 2, 3});

    @BeforeEach
    void setup() {
        service = new RequestAttachmentService(requests, attachments, audit, storage,
                new AttachmentProperties(Path.of("unused-in-unit-test"), DataSize.ofMegabytes(10)), manager);
    }

    @Test
    void savesMetadataAndAuditBeforeCommitUsingGeneratedKey() throws Exception {
        when(requests.existsById(requestId)).thenReturn(true);
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(attachments.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.upload(requestId, file, " user-001 ");
        var metadata = ArgumentCaptor.forClass(RequestAttachment.class);
        var calls = inOrder(storage, manager, attachments, audit);
        calls.verify(storage).store(eq("requests/" + requestId + "/" + response.id()), any());
        calls.verify(manager).getTransaction(any());
        calls.verify(attachments).save(metadata.capture());
        calls.verify(audit).record(requestId, RequestAuditAction.ATTACHMENT_ADDED, "user-001",
                "attachmentId=" + response.id(), response.uploadedAt());
        calls.verify(manager).commit(transaction);
        assertThat(metadata.getValue().getStorageKey()).doesNotContain(file.getOriginalFilename());
        assertThat(response.originalFilename()).isEqualTo("diagnostic.txt");
        assertThat(response.contentType()).isEqualTo("text/plain");
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.requestId()).isEqualTo(requestId);
        verify(storage, never()).delete(any());
    }

    @Test
    void failedDatabaseSaveRollsBackAndCleansStoredFile() throws Exception {
        when(requests.existsById(requestId)).thenReturn(true);
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(attachments.save(any())).thenThrow(new DataIntegrityViolationException("database failure"));
        assertThatThrownBy(() -> service.upload(requestId, file, null)).isInstanceOf(AttachmentException.class)
                .extracting("code").isEqualTo("ATTACHMENT_PERSISTENCE_ERROR");
        verify(manager).rollback(transaction);
        verify(storage).delete(any());
        verifyNoInteractions(audit);
    }

    @Test
    void missingRequestDoesNotWriteFile() {
        assertThatThrownBy(() -> service.upload(requestId, file, null)).isInstanceOf(RequestNotFoundException.class);
        verifyNoInteractions(storage, attachments, audit, manager);
    }

    @Test
    void rejectsEmptyFileBeforeStorage() {
        when(requests.existsById(requestId)).thenReturn(true);
        var empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.upload(requestId, empty, null)).isInstanceOf(AttachmentException.class)
                .extracting("code").isEqualTo("ATTACHMENT_EMPTY");
        verifyNoInteractions(storage, attachments, audit, manager);
    }

    @Test
    void rejectsTraversalFilenameBeforeStorage() {
        when(requests.existsById(requestId)).thenReturn(true);
        var traversal = new MockMultipartFile("file", "../../secret.txt", "text/plain", new byte[]{1});
        assertThatThrownBy(() -> service.upload(requestId, traversal, null)).isInstanceOf(AttachmentException.class)
                .extracting("code").isEqualTo("ATTACHMENT_INVALID_FILENAME");
        verifyNoInteractions(storage, attachments, audit, manager);
    }
}
