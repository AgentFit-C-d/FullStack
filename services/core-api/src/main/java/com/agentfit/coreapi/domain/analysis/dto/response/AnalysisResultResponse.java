package com.agentfit.coreapi.domain.analysis.dto.response;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisResult;
import java.time.Instant;
import java.util.Map;

public record AnalysisResultResponse(
        long attemptId,
        String requestId,
        long generation,
        String contractVersion,
        Map<String, Object> result,
        Instant createdAt) {
    public static AnalysisResultResponse from(AnalysisResult value) {
        return new AnalysisResultResponse(
                value.attemptId(),
                value.requestId(),
                value.generation(),
                value.contractVersion(),
                value.result(),
                value.createdAt());
    }
}
