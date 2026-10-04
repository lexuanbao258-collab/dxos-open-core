package com.tricore.dxos.request.service;

import com.tricore.dxos.request.domain.InvalidRequestTransitionException;
import com.tricore.dxos.request.domain.Request;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import com.tricore.dxos.request.domain.RequestAction;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.domain.RequestStatusHistory;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.dto.RequestHistoryResponse;
import com.tricore.dxos.request.repository.RequestRepository;
import com.tricore.dxos.request.repository.RequestStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestWorkflowServiceTest {
    @Mock private RequestAuditService audit;
    @Mock private RequestRepository requests;
    @Mock private RequestStatusHistoryRepository history;
    @InjectMocks private RequestWorkflowService service;

    private final UUID id = UUID.randomUUID();

    @Test
    void assignmentSavesRequestAndExactlyOneHistoryEntry() {
        Request request = newRequest();
        when(requests.findById(id)).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);
        Instant previous = request.getUpdatedAt();

        RequestResponse response = service.assign(id, "it-user-001", "anonymous");

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.status()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(response.assigneeId()).isEqualTo("it-user-001");
        assertThat(response.resolution()).isNull();
        assertThat(response.updatedAt()).isAfter(previous);
        ArgumentCaptor<RequestStatusHistory> entry = ArgumentCaptor.forClass(RequestStatusHistory.class);
        verify(requests).saveAndFlush(request);
        verify(history).save(entry.capture());
        assertThat(entry.getValue().getRequestId()).isEqualTo(id);
        assertThat(entry.getValue().getFromStatus()).isEqualTo(RequestStatus.NEW);
        assertThat(entry.getValue().getToStatus()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(entry.getValue().getAction()).isEqualTo(RequestAction.ASSIGN);
        assertThat(entry.getValue().getChangedAt()).isEqualTo(response.updatedAt());
    }

    @Test
    void invalidAssignmentLeavesRequestUnchangedAndSavesNoHistory() {
        Request request = newRequest();
        request.assign("first-user");
        Instant previous = request.getUpdatedAt();
        when(requests.findById(id)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.assign(id, "second-user", "anonymous"))
                .isInstanceOf(InvalidRequestTransitionException.class);
        assertThat(request.getAssigneeId()).isEqualTo("first-user");
        assertThat(request.getUpdatedAt()).isEqualTo(previous);
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(history, audit);
    }

    @Test
    void missingRequestCreatesNoHistory() {
        when(requests.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(id, "it-user-001", "anonymous")).isInstanceOf(RequestNotFoundException.class);
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(history, audit);
    }

    @Test
    void completesLifecycleSavingOneMatchingHistoryRecordPerAction() {
        Request request = newRequest();
        when(requests.findById(id)).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);

        service.assign(id, "it-user-001", "anonymous");
        service.start(id, "anonymous");
        RequestResponse resolved = service.resolve(id, "Restarted print service", "anonymous");
        service.confirm(id, "anonymous");
        RequestResponse closed = service.close(id, "anonymous");

        assertThat(resolved.status()).isEqualTo(RequestStatus.RESOLVED);
        assertThat(resolved.resolution()).isEqualTo("Restarted print service");
        assertThat(closed.status()).isEqualTo(RequestStatus.CLOSED);
        assertThat(closed.assigneeId()).isEqualTo("it-user-001");
        assertThat(closed.resolution()).isEqualTo("Restarted print service");
        assertThat(closed.version()).isEqualTo(request.getVersion());
        ArgumentCaptor<RequestStatusHistory> entries = ArgumentCaptor.forClass(RequestStatusHistory.class);
        verify(requests, times(5)).saveAndFlush(request);
        verify(history, times(5)).save(entries.capture());
        List<RequestStatusHistory> records = entries.getAllValues();
        ArgumentCaptor<RequestAuditAction> auditActions = ArgumentCaptor.forClass(RequestAuditAction.class);
        verify(audit, times(5)).record(eq(id), auditActions.capture(), eq("anonymous"), isNull(), any());
        assertThat(auditActions.getAllValues()).containsExactly(RequestAuditAction.REQUEST_ASSIGNED, RequestAuditAction.REQUEST_STARTED, RequestAuditAction.REQUEST_RESOLVED, RequestAuditAction.REQUEST_CONFIRMED, RequestAuditAction.REQUEST_CLOSED);
        assertThat(records).extracting(RequestStatusHistory::getAction)
                .containsExactly(RequestAction.ASSIGN, RequestAction.START, RequestAction.RESOLVE,
                        RequestAction.CONFIRM, RequestAction.CLOSE);
        assertThat(records).extracting(RequestStatusHistory::getFromStatus)
                .containsExactly(RequestStatus.NEW, RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS,
                        RequestStatus.RESOLVED, RequestStatus.CONFIRMED);
        assertThat(records).extracting(RequestStatusHistory::getToStatus)
                .containsExactly(RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS, RequestStatus.RESOLVED,
                        RequestStatus.CONFIRMED, RequestStatus.CLOSED);
        assertThat(records).allSatisfy(entry -> assertThat(entry.getRequestId()).isEqualTo(id));
        assertThat(records).extracting(RequestStatusHistory::getChangedAt).isSorted();
        assertThat(records.get(4).getChangedAt()).isEqualTo(closed.updatedAt());
    }

    @Test
    void failedResolveSavesNothingAndDoesNotChangeTimestamp() {
        Request request = newRequest();
        when(requests.findById(id)).thenReturn(Optional.of(request));
        Instant previous = request.getUpdatedAt();

        assertThatThrownBy(() -> service.resolve(id, "Restarted print service", "anonymous"))
                .isInstanceOf(InvalidRequestTransitionException.class);
        assertThat(request.getResolution()).isNull();
        assertThat(request.getUpdatedAt()).isEqualTo(previous);
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(history, audit);
    }

    @Test
    void historyUsesDeterministicQueryAndMapsChronologicalResults() {
        Request request = newRequest();
        Instant firstAt = Instant.parse("2026-10-04T01:00:00Z");
        Instant secondAt = firstAt.plusSeconds(1);
        when(requests.findById(id)).thenReturn(Optional.of(request));
        when(history.findAllByRequestIdOrderByChangedAtAscIdAsc(id)).thenReturn(List.of(
                new RequestStatusHistory(id, RequestStatus.NEW, RequestStatus.ASSIGNED, RequestAction.ASSIGN, firstAt),
                new RequestStatusHistory(id, RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS, RequestAction.START, secondAt)));

        assertThat(service.history(id)).containsExactly(
                new RequestHistoryResponse(RequestStatus.NEW, RequestStatus.ASSIGNED, RequestAction.ASSIGN, firstAt),
                new RequestHistoryResponse(RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS, RequestAction.START, secondAt));
        verify(history).findAllByRequestIdOrderByChangedAtAscIdAsc(id);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void existingRequestWithNoTransitionsHasEmptyHistory() {
        when(requests.findById(id)).thenReturn(Optional.of(newRequest()));
        when(history.findAllByRequestIdOrderByChangedAtAscIdAsc(id)).thenReturn(List.of());
        assertThat(service.history(id)).isEmpty();
    }

    @Test
    void historyOfMissingRequestReturnsNotFound() {
        when(requests.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.history(id)).isInstanceOf(RequestNotFoundException.class);
        verifyNoInteractions(history, audit);
    }

    @Test
    void optimisticConflictPropagatesWithoutInsertingHistory() {
        Request request = newRequest();
        when(requests.findById(id)).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenThrow(new ObjectOptimisticLockingFailureException(Request.class, id));

        assertThatThrownBy(() -> service.assign(id, "it-user-001", "anonymous"))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verifyNoInteractions(history, audit);
    }

    @Test
    void responseMapsVersionAfterRepositoryFlush() {
        Request request = newRequest();
        when(requests.findById(id)).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenAnswer(invocation -> {
            // Simulate a flushed repository result, not a concurrency test.
            ReflectionTestUtils.setField(request, "version", 1L);
            return request;
        });
        assertThat(service.assign(id, "it-user-001", "anonymous").version()).isEqualTo(1L);
    }

    private Request newRequest() {
        Request request = new Request("Printer", "Offline", "IT_SUPPORT");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "version", 0L);
        return request;
    }
}
