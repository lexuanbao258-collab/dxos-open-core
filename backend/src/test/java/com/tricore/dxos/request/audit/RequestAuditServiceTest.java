package com.tricore.dxos.request.audit;

import com.tricore.dxos.request.audit.domain.RequestAudit;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import com.tricore.dxos.request.audit.dto.RequestAuditResponse;
import com.tricore.dxos.request.audit.repository.RequestAuditRepository;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.repository.RequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestAuditServiceTest {
    @Mock RequestAuditRepository repository;
    @Mock RequestRepository requests;
    @InjectMocks RequestAuditService service;
    final UUID id = UUID.randomUUID();
    final Instant at = Instant.parse("2026-10-04T01:00:00Z");

    @Test
    void capturesActorActionAndOperationTimestamp() {
        service.record(id, RequestAuditAction.REQUEST_CREATED, " user-001 ", null, at);
        var entry = ArgumentCaptor.forClass(RequestAudit.class);
        verify(repository).save(entry.capture());
        assertThat(entry.getValue().getRequestId()).isEqualTo(id);
        assertThat(entry.getValue().getAction()).isEqualTo(RequestAuditAction.REQUEST_CREATED);
        assertThat(entry.getValue().getActorRef()).isEqualTo("user-001");
        assertThat(entry.getValue().getCreatedAt()).isEqualTo(at);
        assertThat(entry.getValue().getMetadata()).isNull();
    }

    @Test
    void absentActorIsAnonymous() {
        service.record(id, RequestAuditAction.REQUEST_STARTED, null, null, at);
        verify(repository).save(argThat(entry -> entry.getActorRef().equals("anonymous")));
    }

    @Test
    void listingUsesTimestampAndIdOrderIncludingEqualTimestampRecords() {
        when(requests.existsById(id)).thenReturn(true);
        var first = new RequestAudit(id, RequestAuditAction.REQUEST_CREATED, "user-001", null, at);
        var tied = new RequestAudit(id, RequestAuditAction.REQUEST_ASSIGNED, "user-002", null, at);
        var later = new RequestAudit(id, RequestAuditAction.REQUEST_STARTED, "anonymous", null, at.plusSeconds(1));
        when(repository.findAllByRequestIdOrderByCreatedAtAscIdAsc(id)).thenReturn(List.of(first, tied, later));
        assertThat(service.list(id)).containsExactly(RequestAuditResponse.from(first), RequestAuditResponse.from(tied), RequestAuditResponse.from(later));
    }

    @Test
    void missingRequestFailsBeforeQuery() {
        assertThatThrownBy(() -> service.list(id)).isInstanceOf(RequestNotFoundException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void existingRequestWithoutAuditReturnsEmptyList() {
        when(requests.existsById(id)).thenReturn(true);
        when(repository.findAllByRequestIdOrderByCreatedAtAscIdAsc(id)).thenReturn(List.of());
        assertThat(service.list(id)).isEmpty();
    }
}
