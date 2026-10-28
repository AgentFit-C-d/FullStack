package com.agentfit.coreapi.domain.analysis.controller;

import com.agentfit.coreapi.domain.analysis.service.InternalInputAccessService;
import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/inputs")
public class InternalInputController {
    private final InternalInputAccessService inputAccessService;

    public InternalInputController(InternalInputAccessService inputAccessService) {
        this.inputAccessService = inputAccessService;
    }

    @GetMapping("/{uploadId}/metadata")
    public Map<String, Object> metadata(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId,
            @RequestParam String attemptId,
            @RequestParam String leaseId) {
        Map<String, Object> metadata = inputAccessService.metadata(
                authorization,
                uploadId,
                attemptId,
                leaseId);
        return Map.of(
                "inputRef", inputAccessService.reference(
                        authorization,
                        uploadId,
                        attemptId,
                        leaseId),
                "metadata", metadata);
    }

    @GetMapping("/{uploadId}")
    public ResponseEntity<byte[]> content(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String uploadId,
            @RequestParam String attemptId,
            @RequestParam String leaseId) {
        PlanningInput input = inputAccessService.requireInput(
                authorization,
                uploadId,
                attemptId,
                leaseId);
        MediaType mediaType = input.mediaType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(input.mediaType());
        byte[] content = input.rawContent() == null
                ? input.extractedText().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                : input.rawContent();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.length)
                .body(content);
    }
}
