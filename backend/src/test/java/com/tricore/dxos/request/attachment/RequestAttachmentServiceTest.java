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
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.TransactionSystemException;
import com.tricore.dxos.request.attachment.dto.AttachmentResponse;
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
    void listingUsesChronologicalQueryAndMapsMetadataOnly() {
        when(requests.existsById(requestId)).thenReturn(true);
        Instant at = Instant.parse("2026-10-04T01:00:00Z");
        var first = new RequestAttachment(UUID.randomUUID(), requestId, "first.txt", null, 1, "private-key-1", at);
        var second = new RequestAttachment(UUID.randomUUID(), requestId, "second.txt", "text/plain", 2, "private-key-2", at.plusSeconds(1));
        when(attachments.findAllByRequestIdOrderByUploadedAtAscIdAsc(requestId)).thenReturn(List.of(first, second));
        assertThat(service.list(requestId)).containsExactly(AttachmentResponse.from(first), AttachmentResponse.from(second));
        verifyNoInteractions(storage, audit);
    }

    @Test
    void listingMissingRequestFailsBeforeAttachmentQuery() {
        assertThatThrownBy(() -> service.list(requestId)).isInstanceOf(RequestNotFoundException.class);
        verifyNoInteractions(attachments, storage, audit);
    }

    @Test
    void commitFailureAlsoCleansStoredFile() throws Exception {
        when(requests.existsById(requestId)).thenReturn(true);
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(attachments.save(any())).thenAnswer(call -> call.getArgument(0));
        doThrow(new TransactionSystemException("commit failure")).when(manager).commit(transaction);
        assertThatThrownBy(() -> service.upload(requestId, file, null)).isInstanceOf(AttachmentException.class)
                .extracting("code").isEqualTo("ATTACHMENT_PERSISTENCE_ERROR");
        verify(storage).delete(any());
        verify(audit).record(eq(requestId), eq(RequestAuditAction.ATTACHMENT_ADDED), eq("anonymous"), anyString(), any());
    }

    @Test
    void auditFailureRollsBackMetadataAndCleansFile() throws Exception {
        when(requests.existsById(requestId)).thenReturn(true);
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(attachments.save(any())).thenAnswer(call -> call.getArgument(0));
        doThrow(new DataIntegrityViolationException("audit failure")).when(audit).record(any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.upload(requestId, file, null)).isInstanceOf(AttachmentException.class);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
        verify(storage).delete(any());
    }

    @Test
    void cleanupFailureDoesNotMaskDatabaseFailure() throws Exception {
        when(requests.existsById(requestId)).thenReturn(true);
        when(manager.getTransaction(any())).thenReturn(transaction);
        var failure = new DataIntegrityViolationException("database failure");
        when(attachments.save(any())).thenThrow(failure);
        doThrow(new IOException("cleanup failure")).when(storage).delete(any());
        assertThatThrownBy(() -> service.upload(requestId, file, null)).isInstanceOf(AttachmentException.class)
                .hasCause(failure).hasMessage("Unable to register attachment");
        verify(manager).rollback(transaction);
    }

    @Test
    void storageFailureDoesNotStartDatabaseTransaction() throws Exception {
        when(requests.existsById(requestId)).thenReturn(true);
        doThrow(new IOException("private storage detail")).when(storage).store(any(), any());
        assertThatThrownBy(() -> service.upload(requestId, file, null)).isInstanceOf(AttachmentException.class)
                .hasMessage("Unable to store attachment");
        verifyNoInteractions(attachments, audit, manager);
    }

    @Test
    void requestDisappearingAfterStoreTriggersCleanupAnd404() throws Exception {
        when(requests.existsById(requestId)).thenReturn(true, false);
        when(manager.getTransaction(any())).thenReturn(transaction);
        assertThatThrownBy(() -> service.upload(requestId, file, null)).isInstanceOf(RequestNotFoundException.class);
        verify(manager).rollback(transaction);
        verify(storage).delete(any());
        verifyNoInteractions(attachments, audit);
    }

    @Test
    void oversizedFileIsRejectedBeforeOpeningStream() throws Exception {
        when(requests.existsById(requestId)).thenReturn(true);
        var oversized = mock(MultipartFile.class);
        when(oversized.getSize()).thenReturn(DataSize.ofMegabytes(10).toBytes() + 1);
        assertThatThrownBy(() -> service.upload(requestId, oversized, null)).isInstanceOf(AttachmentException.class)
                .extracting("code").isEqualTo("ATTACHMENT_TOO_LARGE");
        verify(oversized, never()).getInputStream();
        verifyNoInteractions(storage, attachments, audit, manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "..", "folder/file.txt", "folder\\file.txt", "bad\nname.txt"})
    void invalidFilenameCannotReachStorage(String filename) {
        when(requests.existsById(requestId)).thenReturn(true);
        var invalid = new MockMultipartFile("file", filename, null, new byte[]{1});
        assertThatThrownBy(() -> service.upload(requestId, invalid, null)).isInstanceOf(AttachmentException.class)
                .extracting("code").isEqualTo("ATTACHMENT_INVALID_FILENAME");
        verifyNoInteractions(storage, attachments, audit, manager);
    }

    @Test
    void excessivelyLongFilenameAndContentTypeAreRejected() {
        when(requests.existsById(requestId)).thenReturn(true);
        var invalidName = new MockMultipartFile("file", "x".repeat(256), null, new byte[]{1});
        var invalidType = new MockMultipartFile("file", "report.txt", "x".repeat(256), new byte[]{1});
        assertThatThrownBy(() -> service.upload(requestId, invalidName, null)).isInstanceOf(AttachmentException.class)
                .extracting("code").isEqualTo("ATTACHMENT_INVALID_FILENAME");
        assertThatThrownBy(() -> service.upload(requestId, invalidType, null)).isInstanceOf(AttachmentException.class)
                .extracting("code").isEqualTo("ATTACHMENT_INVALID_CONTENT_TYPE");
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
