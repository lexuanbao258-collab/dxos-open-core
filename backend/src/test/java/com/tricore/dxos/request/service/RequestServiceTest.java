package com.tricore.dxos.request.service;

import com.tricore.dxos.request.domain.Request;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.dto.CreateRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.repository.RequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {
    @Mock
    private RequestRepository repository;

    @InjectMocks
    private RequestService service;

    @Test
    void createsAndSavesNewRequestAndMapsSavedResult() {
        UUID id = UUID.randomUUID();
        when(repository.save(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            // Simulate the repository assigning an id; this is not a database integration test.
            ReflectionTestUtils.setField(request, "id", id);
            return request;
        });

        RequestResponse result = service.create(
                new CreateRequestDto("Repair printer", "Printer is offline", "IT_SUPPORT"));

        ArgumentCaptor<Request> captured = ArgumentCaptor.forClass(Request.class);
        verify(repository).save(captured.capture());
        Request saved = captured.getValue();
        assertThat(saved.getTitle()).isEqualTo("Repair printer");
        assertThat(saved.getDescription()).isEqualTo("Printer is offline");
        assertThat(saved.getRequestType()).isEqualTo("IT_SUPPORT");
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.NEW);
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.status()).isEqualTo(RequestStatus.NEW);
        assertThat(result.createdAt()).isEqualTo(saved.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void listsPersistedRequestsAsDtos() {
        Request first = persistedRequest(UUID.randomUUID(), "Printer");
        Request second = persistedRequest(UUID.randomUUID(), "Laptop");
        when(repository.findAll()).thenReturn(List.of(first, second));

        assertThat(service.list()).extracting(RequestResponse::id)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void returnsEmptyListWhenNoRequestsExist() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.list()).isEmpty();
    }

    @Test
    void getsPersistedRequestAsDto() {
        UUID id = UUID.randomUUID();
        Request request = persistedRequest(id, "Printer");
        when(repository.findById(id)).thenReturn(Optional.of(request));

        RequestResponse result = service.get(id);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.title()).isEqualTo("Printer");
        assertThat(result.description()).isEqualTo("Needs repair");
        assertThat(result.requestType()).isEqualTo("IT_SUPPORT");
        assertThat(result.status()).isEqualTo(RequestStatus.NEW);
        assertThat(result.createdAt()).isEqualTo(request.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(request.getUpdatedAt());
    }

    @Test
    void throwsNotFoundWhenRepositoryHasNoRequest() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(RequestNotFoundException.class)
                .hasMessage("Request not found: " + id);
    }

    private Request persistedRequest(UUID id, String title) {
        Request request = new Request(title, "Needs repair", "IT_SUPPORT");
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }
}
