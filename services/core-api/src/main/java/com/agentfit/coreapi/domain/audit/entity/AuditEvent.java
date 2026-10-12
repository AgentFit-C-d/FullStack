package com.agentfit.coreapi.domain.audit.entity;


import com.agentfit.coreapi.domain.audit.entity.*;
import com.agentfit.coreapi.domain.audit.service.*;
import java.time.Instant;
import java.util.Map;

public record AuditEvent(long actorId, long projectId, String action, String result,
        Instant occurredAt, String reason, Map<String, String> metadata) {
}



