package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewFreshnessGateTest {
    private static final String HASH = "a".repeat(64);
    private static final Instant EXPIRY = Instant.parse("2026-10-06T10:10:00Z");

    private PreviewBasis basis() {
        return new PreviewBasis("project-1", 2, "profile-1", 3,
            "event-1", 4, 5, 6, "release-1", "b".repeat(64));
    }

    private StoredPreviewState stored() {
        return new StoredPreviewState("preview-1", basis(), HASH,
            EXPIRY.minusSeconds(600), EXPIRY);
    }

    private PreviewFingerprintResult regenerated() {
        return new PreviewFingerprintResult(List.of(), "c".repeat(64), "d".repeat(64), HASH);
    }

    @Test
    void acceptsMatchingPreviewImmediatelyBeforeExpiry() {
        assertDoesNotThrow(() -> PreviewFreshnessGate.requireCurrent(stored(), basis(), HASH,
            regenerated(), EXPIRY.minusNanos(1)));
        assertDoesNotThrow(() -> PreviewFreshnessGate.requireStoredCurrent(stored(), basis(), HASH,
            EXPIRY.minusNanos(1)));
    }

    @Test
    void rejectsChangedCurrentBasisBeforeApproval() {
        PreviewBasis original = basis();
        for (PreviewBasis changed : List.of(
            new PreviewBasis("other-project", 2, "profile-1", 3, "event-1", 4, 5, 6,
                "release-1", "b".repeat(64)),
            new PreviewBasis("project-1", 3, "profile-1", 3, "event-1", 4, 5, 6,
                "release-1", "b".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 3, "event-2", 4, 5, 6,
                "release-1", "b".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 3, "event-1", 4, 5, 7,
                "release-1", "b".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 3, "event-1", 4, 5, 6,
                "release-2", "b".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 3, "event-1", 4, 5, 6,
                "release-1", "e".repeat(64)))) {
            assertNotEquals(original, changed);
            assertThrows(PreviewFreshnessGate.StalePreviewException.class,
                () -> PreviewFreshnessGate.requireCurrent(stored(), changed, HASH,
                    regenerated(), EXPIRY.minusSeconds(1)));
        }
    }

    @Test
    void rejectsAtAndAfterExpiration() {
        for (Instant now : List.of(EXPIRY, EXPIRY.plusNanos(1))) {
            assertThrows(PreviewFreshnessGate.ExpiredPreviewException.class,
                () -> PreviewFreshnessGate.requireCurrent(stored(), basis(), HASH,
                    regenerated(), now));
        }
    }

    @Test
    void rejectsSubmittedOrRegeneratedFingerprintMismatch() {
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> PreviewFreshnessGate.requireCurrent(stored(), basis(), "f".repeat(64),
                regenerated(), EXPIRY.minusSeconds(1)));
        PreviewFingerprintResult changed = new PreviewFingerprintResult(List.of(),
            "c".repeat(64), "d".repeat(64), "f".repeat(64));
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> PreviewFreshnessGate.requireCurrent(stored(), basis(), HASH,
                changed, EXPIRY.minusSeconds(1)));
    }

    @Test
    void requiresCompleteMetadata() {
        assertThrows(PreviewFreshnessGate.InvalidPreviewStateException.class,
            () -> PreviewFreshnessGate.requireCurrent(stored(), null, HASH,
                regenerated(), EXPIRY.minusSeconds(1)));
        assertThrows(PreviewFreshnessGate.InvalidPreviewStateException.class,
            () -> PreviewFreshnessGate.requireCurrent(stored(), basis(), "not-a-hash",
                regenerated(), EXPIRY.minusSeconds(1)));
    }

    @Test
    void rejectsPreviewThatHasNotBeenCreatedYet() {
        StoredPreviewState future = new StoredPreviewState("preview-1", basis(), HASH,
            EXPIRY.minusSeconds(30), EXPIRY);
        assertThrows(PreviewFreshnessGate.InvalidPreviewStateException.class,
            () -> PreviewFreshnessGate.requireStoredCurrent(future, basis(), HASH,
                EXPIRY.minusSeconds(60)));
    }
}
