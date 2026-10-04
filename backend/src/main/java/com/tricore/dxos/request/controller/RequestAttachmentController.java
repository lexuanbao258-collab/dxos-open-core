package com.tricore.dxos.request.controller;

import com.tricore.dxos.request.attachment.dto.AttachmentResponse;
import com.tricore.dxos.request.attachment.service.RequestAttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/requests/{id}/attachments")
public class RequestAttachmentController {
    private final RequestAttachmentService service;

    public RequestAttachmentController(RequestAttachmentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                                     @RequestHeader(value = "X-Actor-Id", defaultValue = "anonymous") String actorRef) {
        return service.upload(id, file, actorRef);
    }

    @GetMapping
    public List<AttachmentResponse> list(@PathVariable UUID id) {
        return service.list(id);
    }
}
