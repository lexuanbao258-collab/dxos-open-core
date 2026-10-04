package com.tricore.dxos.request.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestWorkflowTest {
    @Test
    void followsLifecycleAndUpdatesWorkflowDataAndTimestamps() {
        Request request = requestAt(RequestStatus.NEW);
        Instant createdAt = request.getCreatedAt();
        RequestStatus[] targets = {RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS,
                RequestStatus.RESOLVED, RequestStatus.CONFIRMED, RequestStatus.CLOSED};
        for (int i = 0; i < RequestAction.values().length; i++) {
            Instant previous = request.getUpdatedAt();
            act(request, RequestAction.values()[i]);
            assertThat(request.getStatus()).isEqualTo(targets[i]);
            assertThat(request.getUpdatedAt()).isAfter(previous);
            assertThat(request.getCreatedAt()).isEqualTo(createdAt);
        }
        assertThat(request.getAssigneeId()).isEqualTo("it-user-001");
        assertThat(request.getResolution()).isEqualTo("Restarted print service");
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void rejectsEveryInvalidActionWithoutChangingRequest(RequestStatus status, RequestAction action) {
        Request request = requestAt(status);
        Instant updatedAt = request.getUpdatedAt();
        String assignee = request.getAssigneeId();
        String resolution = request.getResolution();

        assertThatThrownBy(() -> act(request, action))
                .isInstanceOf(InvalidRequestTransitionException.class)
                .hasMessageContaining(status.name()).hasMessageContaining(action.name());
        assertThat(request.getStatus()).isEqualTo(status);
        assertThat(request.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(request.getAssigneeId()).isEqualTo(assignee);
        assertThat(request.getResolution()).isEqualTo(resolution);
    }

    static Stream<Arguments> invalidTransitions() {
        return Arrays.stream(RequestStatus.values()).flatMap(status -> Arrays.stream(RequestAction.values())
                .filter(action -> status != switch (action) {
                    case ASSIGN -> RequestStatus.NEW;
                    case START -> RequestStatus.ASSIGNED;
                    case RESOLVE -> RequestStatus.IN_PROGRESS;
                    case CONFIRM -> RequestStatus.RESOLVED;
                    case CLOSE -> RequestStatus.CONFIRMED;
                })
                .map(action -> Arguments.of(status, action)));
    }

    @Test
    void rejectsInvalidAssignmentAndResolutionWithoutMutating() {
        Request request = requestAt(RequestStatus.NEW);
        Instant updatedAt = request.getUpdatedAt();
        for (String value : Arrays.asList(null, "", " ", "a".repeat(101))) {
            assertThatThrownBy(() -> request.assign(value)).isInstanceOf(IllegalArgumentException.class);
            assertThat(request.getAssigneeId()).isNull();
            assertThat(request.getStatus()).isEqualTo(RequestStatus.NEW);
            assertThat(request.getUpdatedAt()).isEqualTo(updatedAt);
        }
        request.assign("it-user-001");
        request.start();
        Instant inProgressAt = request.getUpdatedAt();
        for (String value : Arrays.asList(null, "", " ", "a".repeat(4001))) {
            assertThatThrownBy(() -> request.resolve(value)).isInstanceOf(IllegalArgumentException.class);
            assertThat(request.getResolution()).isNull();
            assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
            assertThat(request.getUpdatedAt()).isEqualTo(inProgressAt);
        }
    }

    private Request requestAt(RequestStatus target) {
        Request request = new Request("Printer", "Offline", "IT_SUPPORT");
        for (RequestAction action : RequestAction.values()) {
            if (request.getStatus() == target) break;
            act(request, action);
        }
        return request;
    }

    private void act(Request request, RequestAction action) {
        switch (action) {
            case ASSIGN -> request.assign("it-user-001");
            case START -> request.start();
            case RESOLVE -> request.resolve("Restarted print service");
            case CONFIRM -> request.confirm();
            case CLOSE -> request.close();
        }
    }
}
