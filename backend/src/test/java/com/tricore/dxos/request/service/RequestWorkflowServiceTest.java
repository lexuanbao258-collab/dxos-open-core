package com.tricore.dxos.request.service;

import com.tricore.dxos.request.domain.InvalidRequestTransitionException;
import com.tricore.dxos.request.domain.Request;
import com.tricore.dxos.request.domain.RequestAction;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.domain.RequestStatusHistory;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.repository.RequestRepository;
import com.tricore.dxos.request.repository.RequestStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestWorkflowServiceTest {
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

        RequestResponse response = service.assign(id, "it-user-001");

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

        assertThatThrownBy(() -> service.assign(id, "second-user"))
                .isInstanceOf(InvalidRequestTransitionException.class);
        assertThat(request.getAssigneeId()).isEqualTo("first-user");
        assertThat(request.getUpdatedAt()).isEqualTo(previous);
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(history);
    }

    @Test
    void missingRequestCreatesNoHistory() {
        when(requests.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(id, "it-user-001")).isInstanceOf(RequestNotFoundException.class);
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(history);
    }

    private Request newRequest() {
        Request request = new Request("Printer", "Offline", "IT_SUPPORT");
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "version", 0L);
        return request;
    }
}
