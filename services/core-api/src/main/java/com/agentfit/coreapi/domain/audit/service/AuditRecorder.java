package com.agentfit.coreapi.domain.audit.service;


import com.agentfit.coreapi.domain.audit.entity.*;
import com.agentfit.coreapi.domain.audit.service.*;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class AuditRecorder {
    private static final Set<String> BLOCKED_KEYS = Set.of(
        "secret", "signature", "rawBody", "rawResponse", "accessToken", "refreshToken",
        "raw_content", "rawContent", "inputText", "document", "extracted_text", "extractedText", "citation", "citations");

    private final AuditSink sink;

    public AuditRecorder() {
        this(event -> { });
    }

    @Autowired
    public AuditRecorder(ObjectProvider<AuditSink> sinks) {
        this(sinks.getIfAvailable(() -> event -> { }));
    }

    public AuditRecorder(AuditSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    public AuditEvent record(AuditEvent event) {
        AuditEvent safe = sanitize(event);
        sink.accept(safe);
        return safe;
    }

    public AuditEvent sanitize(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("audit event is required");
        }
        Map<String, String> safeMetadata = event.metadata() == null ? Map.of()
            : event.metadata().entrySet().stream()
                .filter(entry -> !BLOCKED_KEYS.contains(entry.getKey())
                    && BLOCKED_KEYS.stream().noneMatch(blocked -> entry.getKey().toLowerCase().contains(blocked.toLowerCase())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new AuditEvent(event.actorId(), event.projectId(), event.action(), event.result(),
            event.occurredAt(), event.reason(), safeMetadata);
    }

    @FunctionalInterface
    public interface AuditSink {
        void accept(AuditEvent event);
    }
}



