package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.audit.dto.RequestAuditResponse;
import com.tricore.dxos.request.audit.domain.RequestAuditAction;
import com.tricore.dxos.request.audit.service.RequestAuditService;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RequestAuditController.class)
class RequestAuditControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private RequestAuditService service;
    private final UUID id = UUID.randomUUID();

    @Test
    void returnsAuditDtosInServiceOrder() throws Exception {
        Instant at = Instant.parse("2026-10-04T01:00:00Z");
        when(service.list(id)).thenReturn(List.of(
                new RequestAuditResponse(RequestAuditAction.REQUEST_CREATED, "user-001", null, at),
                new RequestAuditResponse(RequestAuditAction.REQUEST_ASSIGNED, "anonymous", null, at.plusSeconds(1))));
        mvc.perform(get("/api/v1/requests/{id}/audit", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("REQUEST_CREATED"))
                .andExpect(jsonPath("$[0].actorRef").value("user-001"))
                .andExpect(jsonPath("$[1].action").value("REQUEST_ASSIGNED"))
                .andExpect(jsonPath("$[1].createdAt").value(at.plusSeconds(1).toString()));
    }

    @Test
    void missingRequestReturnsConsistent404() throws Exception {
        when(service.list(id)).thenThrow(new RequestNotFoundException(id));
        mvc.perform(get("/api/v1/requests/{id}/audit", id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/requests/bad-id/audit")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ID"));
        verifyNoInteractions(service);
    }
}
