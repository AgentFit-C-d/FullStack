package com.agentfit.coreapi.domain.analysis.entity;

import java.time.Instant;

public record AnalysisInputLease(
        String attemptId,
        String uploadId,
        String leaseId,
        String projectId,
        long inputRevision,
        Instant expiresAt,
        Instant createdAt) {

    public boolean isUsableAt(Instant now) {
        return now != null && expiresAt != null && now.isBefore(expiresAt);
    }
}
