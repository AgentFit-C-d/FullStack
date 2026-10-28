package com.agentfit.coreapi.domain.analysis.entity;

import java.time.Instant;
import java.util.Map;

public record AnalysisResult(
        String attemptId,
        String requestId,
        long generation,
        String contractVersion,
        Map<String, Object> result,
        Instant createdAt) {
}
