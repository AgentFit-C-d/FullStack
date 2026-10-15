package com.agentfit.coreapi.domain.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.domain.audit.entity.AuditEvent;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AuditTransactionTest {
    @Test
    void successfulEventIsSanitizedBeforeTheSinkAndFailureDoesNotRecord() {
        AtomicInteger count = new AtomicInteger();
        AuditRecorder recorder = new AuditRecorder(event -> count.incrementAndGet());
        AuditEvent event = recorder.record(new AuditEvent(1L, "p-1", "DELETE", "SUCCEEDED", Instant.now(),
            "confirmed", Map.of("secret", "never-store", "target", "PROJECT")));
        assertEquals(1, count.get());
        assertFalse(event.metadata().containsKey("secret"));
        assertThrows(IllegalArgumentException.class, () -> recorder.record(null));
        assertEquals(1, count.get());
    }
}
