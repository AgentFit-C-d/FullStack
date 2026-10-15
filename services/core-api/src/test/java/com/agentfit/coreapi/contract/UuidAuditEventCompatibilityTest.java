package com.agentfit.coreapi.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.agentfit.coreapi.domain.audit.entity.AuditEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidAuditEventCompatibilityTest {
    @Test
    void uuidAuditEventCarriesCanonicalIdentitiesWithoutBreakingLegacyFixture() {
        UUID actor = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        AuditEvent event = new AuditEvent(actor, project, "RECOMMENDATION_FAILED", "FAILED",
            Instant.now(), "ANALYSIS_UNCONFIRMED", Map.of());

        assertEquals(actor, event.actorUuid());
        assertEquals(project, event.projectUuid());
        assertEquals(project.toString(), event.projectId());

        AuditEvent legacy = new AuditEvent(7L, "legacy-project", "DELETE", "SUCCEEDED",
            Instant.now(), null, Map.of());
        assertEquals(7L, legacy.actorId());
        assertNull(legacy.actorUuid());
    }
}
