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

        assertTrue(event.actorId() == 7L && event.projectId().equals("11"));
        assertTrue(event.metadata().containsKey("requestId"));
        assertFalse(event.metadata().containsKey("secret"));
        assertFalse(event.metadata().containsKey("signature"));
        assertFalse(event.metadata().containsKey("rawResponse"));
    }

    @Test
    void removesPlanningSourceVariantsFromAuditMetadata() {
        AuditEvent safe = new AuditRecorder().sanitize(new AuditEvent(1L, "p", "ANALYSIS", "SUCCEEDED", Instant.now(), null,
            Map.of("raw_content", "secret", "inputText", "secret", "extracted_text", "secret", "citation", "secret", "state", "PROCESSING")));
        assertFalse(safe.metadata().containsKey("raw_content"));
        assertFalse(safe.metadata().containsKey("inputText"));
        assertFalse(safe.metadata().containsKey("extracted_text"));
        assertFalse(safe.metadata().containsKey("citation"));
        assertTrue(safe.metadata().containsKey("state"));
    }
}



