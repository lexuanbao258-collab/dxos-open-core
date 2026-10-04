package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.domain.InvalidRequestTransitionException;
import com.tricore.dxos.request.domain.RequestAction;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.dto.RequestHistoryResponse;
import com.tricore.dxos.request.service.RequestWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RequestWorkflowController.class)
class RequestWorkflowControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private RequestWorkflowService service;
    private final UUID id = UUID.randomUUID();
    private final Instant timestamp = Instant.parse("2026-10-04T01:00:00Z");

    @Test
    void assignsRequest() throws Exception {
        when(service.assign(id, "it-user-001")).thenReturn(response(RequestStatus.ASSIGNED));

        mvc.perform(post("/api/v1/requests/" + id + "/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"it-user-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assigneeId").value("it-user-001"))
                .andExpect(jsonPath("$.version").value(1));
        verify(service).assign(id, "it-user-001");
    }

    @Test
    void rejectsBlankAssignee() throws Exception {
        mvc.perform(post("/api/v1/requests/" + id + "/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.assigneeId").exists());
        verifyNoInteractions(service);
    }

    @Test
    void missingRequestReturns404() throws Exception {
        when(service.assign(id, "it-user-001")).thenThrow(new RequestNotFoundException(id));
        mvc.perform(post("/api/v1/requests/" + id + "/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"it-user-001\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void invalidTransitionReturns409() throws Exception {
        when(service.assign(id, "it-user-001"))
                .thenThrow(new InvalidRequestTransitionException(RequestStatus.CLOSED, RequestAction.ASSIGN));
        mvc.perform(post("/api/v1/requests/" + id + "/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"it-user-001\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Cannot perform ASSIGN while Request status is CLOSED"));
    }

    @Test
    void startsAssignedRequestWithoutBody() throws Exception {
        when(service.start(id)).thenReturn(response(RequestStatus.IN_PROGRESS));
        mvc.perform(post("/api/v1/requests/" + id + "/start"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        verify(service).start(id);
    }

    @Test
    void resolvesRequest() throws Exception {
        when(service.resolve(id, "Restarted print service")).thenReturn(new RequestResponse(
                id, "Printer", "Offline", "IT_SUPPORT", RequestStatus.RESOLVED,
                "it-user-001", "Restarted print service", 3L, timestamp, timestamp));
        mvc.perform(post("/api/v1/requests/" + id + "/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"Restarted print service\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("Restarted print service"));
        verify(service).resolve(id, "Restarted print service");
    }

    @Test
    void confirmsResolvedRequestWithoutBody() throws Exception {
        when(service.confirm(id)).thenReturn(response(RequestStatus.CONFIRMED));
        mvc.perform(post("/api/v1/requests/" + id + "/confirm"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        verify(service).confirm(id);
    }

    @Test
    void closesConfirmedRequestWithoutBody() throws Exception {
        when(service.close(id)).thenReturn(response(RequestStatus.CLOSED));
        mvc.perform(post("/api/v1/requests/" + id + "/close"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        verify(service).close(id);
    }

    @Test
    void rejectsBlankResolution() throws Exception {
        mvc.perform(post("/api/v1/requests/" + id + "/resolve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.resolution").exists());
        verifyNoInteractions(service);
    }

    @Test
    void returnsHistoryDtos() throws Exception {
        when(service.history(id)).thenReturn(List.of(new RequestHistoryResponse(
                RequestStatus.NEW, RequestStatus.ASSIGNED, RequestAction.ASSIGN, timestamp)));
        mvc.perform(get("/api/v1/requests/" + id + "/history"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromStatus").value("NEW"))
                .andExpect(jsonPath("$[0].toStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$[0].action").value("ASSIGN"))
                .andExpect(jsonPath("$[0].changedAt").value(timestamp.toString()));
    }

    @Test
    void missingRequestHistoryReturns404() throws Exception {
        when(service.history(id)).thenThrow(new RequestNotFoundException(id));
        mvc.perform(get("/api/v1/requests/" + id + "/history"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }

    @ParameterizedTest
    @MethodSource("optimisticConflicts")
    void optimisticConflictReturns409(RuntimeException conflict) throws Exception {
        when(service.start(id)).thenThrow(conflict);
        mvc.perform(post("/api/v1/requests/" + id + "/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("REQUEST_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Request was updated concurrently; reload it and retry"));
    }

    static Stream<RuntimeException> optimisticConflicts() {
        return Stream.of(new ObjectOptimisticLockingFailureException("Request", UUID.randomUUID()),
                new OptimisticLockException("Internal persistence detail"));
    }

    private RequestResponse response(RequestStatus status) {
        return new RequestResponse(id, "Printer", "Offline", "IT_SUPPORT", status,
                "it-user-001", null, 1L, timestamp, timestamp);
    }
}
