package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisAttemptRepository {
    default AnalysisAttempt start(AnalysisAttempt attempt) { return attempt; }
    default Optional<AnalysisAttempt> findByRequestId(String requestId) { return Optional.empty(); }
    default void complete(String requestId, String state, Instant completedAt) { }
    default Optional<AnalysisAttempt> findProcessingByProjectId(String projectId) { return Optional.empty(); }
    default Optional<AnalysisAttempt> findProcessingByOwnerId(long ownerId) { return Optional.empty(); }
    default Optional<AnalysisAttempt> findProcessingByOwnerUuid(UUID ownerId) { return Optional.empty(); }
    default Optional<AnalysisAttempt> findLatestByProjectId(String projectId) { return Optional.empty(); }
    default Optional<AnalysisAttempt> claimNext(Instant now, Instant leaseExpiresAt) { return Optional.empty(); }
    default java.util.List<AnalysisAttempt> findExpired(Instant now) { return java.util.List.of(); }
    default boolean transitionToSucceeded(String id, UUID executionToken, String draftId, long draftVersion, Instant finishedAt) { return false; }
    default boolean transitionToTerminal(String id, UUID executionToken, String status, String errorCode, Instant finishedAt) { return false; }
    default boolean transitionToTerminal(String id, String status, String errorCode, Instant finishedAt) {
        return transitionToTerminal(0L, status, errorCode, finishedAt);
    }
    /** Compatibility shim for old in-memory unit fixtures. Production repositories use UUID strings. */
    default boolean transitionToTerminal(long id, String state, String errorCode, Instant completedAt) { return false; }
}
