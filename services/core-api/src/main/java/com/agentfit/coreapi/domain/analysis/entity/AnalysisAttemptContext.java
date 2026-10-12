package com.agentfit.coreapi.domain.analysis.entity;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import java.time.Instant;

public record AnalysisAttemptContext(String requestId, String projectId, long generation,
        String contractVersion, Instant deadlineAt, String state) {
    public boolean isAcceptable(Instant now) {
        return "PROCESSING".equals(state) && deadlineAt != null && now.isBefore(deadlineAt);
    }

    public boolean matches(AiAnalysisClient.AnalysisResponse response) {
        return response != null && requestId.equals(response.requestId())
            && generation == response.generation() && contractVersion.equals(response.contractVersion());
    }
}



