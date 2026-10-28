package com.agentfit.coreapi.domain.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisInputLease;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnalysisInputLeaseTest {
    @Test
    void leaseIsUsableOnlyBeforeItsExpiry() {
        Instant expiresAt = Instant.parse("2026-10-27T18:00:00Z");
        AnalysisInputLease lease = new AnalysisInputLease(
                "11111111-1111-1111-1111-111111111111",
                "upload-1",
                "lease-1",
                "22222222-2222-2222-2222-222222222222",
                1L,
                expiresAt,
                expiresAt.minusSeconds(60));

        assertTrue(lease.isUsableAt(expiresAt.minusSeconds(1)));
        assertFalse(lease.isUsableAt(expiresAt));
        assertFalse(lease.isUsableAt(null));
    }
}
