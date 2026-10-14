package com.agentfit.coreapi.domain.project.entity;

import java.time.Instant;

public record ProjectCreationIdempotency(long ownerId, String operation, String idempotencyKey,
        String requestFingerprint, String projectId, Instant createdAt) { }
