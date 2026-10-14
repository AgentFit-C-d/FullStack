package com.agentfit.coreapi.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.audit.entity.AuditEvent;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import com.agentfit.coreapi.domain.audit.service.FailureAuditService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FailureAuditSafetyTest {
    @Test
    void recordsOnlyMinimalFailureAuditAfterBusinessRollback() {
        AtomicReference<AuditEvent> received = new AtomicReference<>();
        FailureAuditService service = new FailureAuditService(
            new AuditRecorder(received::set));

        service.record(new AuditEvent(7L, "project-1", "RECOMMENDATION_REJECTED", "FAILED",
            Instant.parse("2026-01-01T00:00:00Z"), "STALE_BASIS",
            Map.of("requestId", "req-1", "rawResponse", "secret-value", "target", "PROJECT")));

        AuditEvent event = received.get();
        assertEquals("STALE_BASIS", event.reason());
        assertEquals("req-1", event.metadata().get("requestId"));
        assertFalse(event.metadata().containsKey("rawResponse"));
        assertFalse(event.metadata().containsValue("secret-value"));
    }
}
