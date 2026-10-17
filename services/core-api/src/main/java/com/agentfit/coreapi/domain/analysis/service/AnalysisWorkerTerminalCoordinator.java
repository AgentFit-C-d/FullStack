package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;

/** Core-owned terminal handling; the AI service never writes a profile or attempt directly. */
public interface AnalysisWorkerTerminalCoordinator {
    void succeed(AnalysisAttempt attempt, AiAnalysisClient.AnalysisResponse response);
    void fail(AnalysisAttempt attempt, String errorCode);
}
