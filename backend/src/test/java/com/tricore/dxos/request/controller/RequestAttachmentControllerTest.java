package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.attachment.dto.AttachmentResponse;
import com.tricore.dxos.request.attachment.service.RequestAttachmentService;
import com.tricore.dxos.request.domain.RequestNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RequestAttachmentController.class)
class RequestAttachmentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean RequestAttachmentService service;
    final UUID id = UUID.randomUUID();
    final MockMultipartFile file = new MockMultipartFile("file", "report.txt", "text/plain", new byte[]{1});

    @Test
    void uploadReturnsDtoAndForwardsActorWithoutExposingStorageKey() throws Exception {
        var response = new AttachmentResponse(UUID.randomUUID(), id, "report.txt", "text/plain", 1, Instant.now());
        when(service.upload(id, file, "user-001")).thenReturn(response);
        mvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file).header("X-Actor-Id", "user-001"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.originalFilename").value("report.txt"))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void absentActorUsesAnonymous() throws Exception {
        mvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file)).andExpect(status().isCreated());
        verify(service).upload(id, file, "anonymous");
    }

    @Test
    void missingFileReturns400() throws Exception {
        mvc.perform(multipart("/api/v1/requests/{id}/attachments", id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_FILE_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test
    void missingRequestReturns404() throws Exception {
        when(service.upload(id, file, "anonymous")).thenThrow(new RequestNotFoundException(id));
        mvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }
}
