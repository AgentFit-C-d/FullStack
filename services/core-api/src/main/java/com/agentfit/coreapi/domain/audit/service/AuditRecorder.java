package com.agentfit.coreapi.domain.audit.service;


import com.agentfit.coreapi.domain.audit.entity.*;
import com.agentfit.coreapi.domain.audit.service.*;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class AuditRecorder {
    private static final Set<String> BLOCKED_KEYS = Set.of(
        "secret", "signature", "rawBody", "rawResponse", "accessToken", "refreshToken");

    public AuditEvent sanitize(AuditEvent event) {
        Map<String, String> safeMetadata = event.metadata() == null ? Map.of()
            : event.metadata().entrySet().stream()
                .filter(entry -> !BLOCKED_KEYS.contains(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new AuditEvent(event.actorId(), event.projectId(), event.action(), event.result(),
            event.occurredAt(), event.reason(), safeMetadata);
    }
}



