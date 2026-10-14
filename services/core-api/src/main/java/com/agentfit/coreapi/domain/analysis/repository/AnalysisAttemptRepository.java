package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import java.time.Instant;
import java.util.Optional;

public interface AnalysisAttemptRepository {
    default AnalysisAttempt start(AnalysisAttempt attempt) { return attempt; }
    default Optional<AnalysisAttempt> findByRequestId(String requestId) { return Optional.empty(); }
    default void complete(String requestId, String state, Instant completedAt) { }
    default Optional<AnalysisAttempt> findProcessingByProjectId(String projectId) { return Optional.empty(); }
    default Optional<AnalysisAttempt> findProcessingByOwnerId(long ownerId) { return Optional.empty(); }
    default Optional<AnalysisAttempt> findLatestByProjectId(String projectId) { return Optional.empty(); }
    default java.util.List<AnalysisAttempt> findExpired(Instant now) { return java.util.List.of(); }
    default boolean transitionToTerminal(long id, String state, String errorCode, Instant completedAt) { return false; }
}
