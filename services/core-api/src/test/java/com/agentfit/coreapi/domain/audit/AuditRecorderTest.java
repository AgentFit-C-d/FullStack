package com.agentfit.coreapi.domain.audit;


import com.agentfit.coreapi.domain.audit.entity.*;
import com.agentfit.coreapi.domain.audit.service.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditRecorderTest {
    @Test
    void recordsMinimumFieldsAndRemovesSecretsAndRawResponses() {
        AuditRecorder recorder = new AuditRecorder();
        AuditEvent event = recorder.sanitize(new AuditEvent(7L, 11L, "ANALYSIS", "SUCCEEDED", Instant.now(),
            "done", Map.of("secret", "hmac-secret", "signature", "raw-signature", "requestId", "r-1")));

        assertTrue(event.actorId() == 7L && event.projectId() == 11L);
        assertTrue(event.metadata().containsKey("requestId"));
        assertFalse(event.metadata().containsKey("secret"));
        assertFalse(event.metadata().containsKey("signature"));
        assertFalse(event.metadata().containsKey("rawResponse"));
    }
}



