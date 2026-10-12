package com.agentfit.coreapi.domain.analysis.entity;

import java.time.Instant;

/** Durable A6 execution state used to reject stale and late AI results. */
public record AnalysisAttempt(long id, String projectId, String requestId, long generation,
        String contractVersion, String state, Instant deadlineAt, Instant startedAt, Instant completedAt) {
    public boolean acceptsResult(Instant now) {
        return "PROCESSING".equals(state) && deadlineAt != null && now != null && now.isBefore(deadlineAt);
    }
}
