package com.agentfit.coreapi.domain.project.entity;

import java.time.Instant;
import java.util.UUID;

public record ProjectCreationIdempotency(long ownerId, String operation, String idempotencyKey,
        String requestFingerprint, String projectId, Instant createdAt, UUID ownerUuid) {
    public ProjectCreationIdempotency(long ownerId, String operation, String idempotencyKey,
            String requestFingerprint, String projectId, Instant createdAt) {
        this(ownerId, operation, idempotencyKey, requestFingerprint, projectId, createdAt, null);
    }

    public ProjectCreationIdempotency(UUID ownerUuid, String operation, String idempotencyKey,
            String requestFingerprint, String projectId, Instant createdAt) {
        this(0L, operation, idempotencyKey, requestFingerprint, projectId, createdAt, ownerUuid);
    }
}
