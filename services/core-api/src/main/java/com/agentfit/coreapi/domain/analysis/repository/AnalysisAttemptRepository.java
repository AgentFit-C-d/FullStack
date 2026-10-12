package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import java.time.Instant;
import java.util.Optional;

public interface AnalysisAttemptRepository {
    default AnalysisAttempt start(AnalysisAttempt attempt) { return attempt; }
    default Optional<AnalysisAttempt> findByRequestId(String requestId) { return Optional.empty(); }
    default void complete(String requestId, String state, Instant completedAt) { }
}
