package com.agentfit.coreapi.domain.project.entity;


import java.time.Instant;
import java.util.UUID;

public record Project(String id, long ownerId, String name, long version, String stage,
        long analysisGeneration, long reviewGeneration, Instant createdAt, Instant updatedAt,
        String developerRole, Long currentInputRevision, UUID ownerUuid) {
    public Project(String id, long ownerId, String name, long version, String stage,
            long analysisGeneration, long reviewGeneration, Instant createdAt, Instant updatedAt,
            String developerRole, Long currentInputRevision) {
        this(id, ownerId, name, version, stage, analysisGeneration, reviewGeneration,
            createdAt, updatedAt, developerRole, currentInputRevision, null);
    }

    public Project(String id, long ownerId, String name, long version, Instant createdAt, Instant updatedAt) {
        this(id, ownerId, name, version, "CREATED", 0L, 0L, createdAt, updatedAt, "BACKEND", null, null);
    }

    public Project(String id, long ownerId, UUID ownerUuid, String name, long version,
            Instant createdAt, Instant updatedAt) {
        this(id, ownerId, name, version, "CREATED", 0L, 0L, createdAt, updatedAt,
            "BACKEND", null, ownerUuid);
    }

    public Project(String id, long ownerId, String name, long version, String stage,
            long analysisGeneration, long reviewGeneration, Instant createdAt, Instant updatedAt) {
        this(id, ownerId, name, version, stage, analysisGeneration, reviewGeneration, createdAt, updatedAt, "BACKEND", null);
    }
}



