package com.agentfit.coreapi.domain.audit.entity;


import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(long actorId, String projectId, String action, String result,
        Instant occurredAt, String reason, Map<String, String> metadata,
        UUID actorUuid, UUID projectUuid) {
    public AuditEvent(long actorId, String projectId, String action, String result,
            Instant occurredAt, String reason, Map<String, String> metadata) {
        this(actorId, projectId, action, result, occurredAt, reason, metadata, null, parseUuid(projectId));
    }

    /** Compatibility constructor for early numeric project fixtures. */
    public AuditEvent(long actorId, long projectId, String action, String result,
            Instant occurredAt, String reason, Map<String, String> metadata) {
        this(actorId, Long.toString(projectId), action, result, occurredAt, reason, metadata);
    }
    public AuditEvent(UUID actorUuid, UUID projectUuid, String action, String result,
            Instant occurredAt, String reason, Map<String, String> metadata) {
        this(0L, projectUuid == null ? null : projectUuid.toString(), action, result,
            occurredAt, reason, metadata, actorUuid, projectUuid);
    }

    private static UUID parseUuid(String value) {
        if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}



