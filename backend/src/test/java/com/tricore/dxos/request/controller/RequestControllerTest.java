package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.domain.RequestStatus;
import com.tricore.dxos.request.dto.CreateRequestDto;
import com.tricore.dxos.request.dto.RequestResponse;
import com.tricore.dxos.request.service.RequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RequestController.class)
class RequestControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RequestService service;

    private final UUID id = UUID.fromString("f3bf54b7-fdab-4b45-85dd-94cbd8587a9d");
    private final Instant timestamp = Instant.parse("2026-10-04T01:00:00Z");
    private final RequestResponse response = new RequestResponse(id, "Repair printer",
            "Printer is offline", "IT_SUPPORT", RequestStatus.NEW, timestamp, timestamp);

    @Test
    void createsRequestWithLocationAndResponseDto() throws Exception {
        CreateRequestDto input = new CreateRequestDto("Repair printer", "Printer is offline", "IT_SUPPORT");
        when(service.create(input)).thenReturn(response);

        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Repair printer","description":"Printer is offline","requestType":"IT_SUPPORT"}
                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/requests/" + id))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdAt").value(timestamp.toString()))
                .andExpect(jsonPath("$.updatedAt").value(timestamp.toString()));
        verify(service).create(input);
    }

    @Test
    void listsRequests() throws Exception {
        when(service.list()).thenReturn(List.of(response));

        mvc.perform(get("/api/v1/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    void returnsExistingRequest() throws Exception {
        when(service.get(id)).thenReturn(response);

        mvc.perform(get("/api/v1/requests/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Repair printer"))
                .andExpect(jsonPath("$.description").value("Printer is offline"))
                .andExpect(jsonPath("$.requestType").value("IT_SUPPORT"));
    }
}
