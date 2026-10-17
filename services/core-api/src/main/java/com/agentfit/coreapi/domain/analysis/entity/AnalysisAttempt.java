package com.agentfit.coreapi.domain.analysis.entity;

import java.time.Instant;
import java.util.UUID;

/** Durable A6 execution state used to reject stale and late AI results. */
public record AnalysisAttempt(String id, String projectId, String requestId, long generation,
        String contractVersion, String status, Instant deadlineAt, Instant startedAt, Instant finishedAt,
        UUID ownerUuid, Long inputRevision, String errorCode, UUID executionToken, Instant claimedAt,
        Instant leaseExpiresAt, Long expectedProjectVersion, String expectedBasis) {
    public AnalysisAttempt(String id, String projectId, String requestId, long generation,
            String contractVersion, String status, Instant deadlineAt, Instant startedAt, Instant finishedAt,
            UUID ownerUuid, Long inputRevision, String errorCode) {
        this(id, projectId, requestId, generation, contractVersion, status, deadlineAt, startedAt, finishedAt,
            ownerUuid, inputRevision, errorCode, null, null, null, null, null);
    }
    public AnalysisAttempt(long id, String projectId, String requestId, long generation, String contractVersion,
            String state, Instant deadlineAt, Instant startedAt, Instant completedAt) {
        this(Long.toString(id),projectId,requestId,generation,contractVersion,state,deadlineAt,startedAt,completedAt,null,null,null);
    }
    public AnalysisAttempt(long id, String projectId, String requestId, long generation, String contractVersion,
            String state, Instant deadlineAt, Instant startedAt, Instant completedAt, Long ownerId,
            Long inputRevision, String errorCode) {
        this(Long.toString(id),projectId,requestId,generation,contractVersion,state,deadlineAt,startedAt,completedAt,ownerId == null ? null : new UUID(0L, ownerId),inputRevision,errorCode);
    }
    public String state() { return status; }
    public Instant completedAt() { return finishedAt; }
    /** Compatibility view used only by pre-V5 in-memory test fixtures. */
    public Long ownerId() {
        return ownerUuid == null ? null : ownerUuid.getLeastSignificantBits();
    }
    public AnalysisAttempt withClaim(UUID token, Instant claimedAt, Instant leaseExpiresAt) {
        return new AnalysisAttempt(id, projectId, requestId, generation, contractVersion, status, deadlineAt,
            startedAt, finishedAt, ownerUuid, inputRevision, errorCode, token, claimedAt, leaseExpiresAt,
            expectedProjectVersion, expectedBasis);
    }
    public boolean acceptsResult(Instant now) {
        return "PROCESSING".equals(status) && deadlineAt != null && now != null && now.isBefore(deadlineAt);
    }
}
