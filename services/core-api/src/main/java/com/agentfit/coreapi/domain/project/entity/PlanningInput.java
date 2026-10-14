package com.agentfit.coreapi.domain.project.entity;

import java.time.Instant;

public record PlanningInput(String projectId, long revision, String inputType, String fileName,
        String mediaType, Long byteSize, Integer characterCount, Integer pageCount,
        byte[] rawContent, String extractedText, String citations, Instant createdAt) {
    public boolean hasRawContent() { return rawContent != null || extractedText != null; }
}
