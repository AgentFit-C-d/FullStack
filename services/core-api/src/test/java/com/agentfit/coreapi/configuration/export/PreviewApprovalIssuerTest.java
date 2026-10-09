package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.history.ApprovedConfigurationGenerator;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewApprovalIssuerTest {
    private static final Instant NOW = Instant.parse("2026-10-09T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PreviewBasis BASIS = new PreviewBasis("project-1", 2,
        "profile-1", 3, "event-1", 4, 5, 6, "catalog-1", "a".repeat(64));

    private PreviewFingerprintInput input(String content) {
        return new PreviewFingerprintInput(BASIS, "rec-1", List.of("tool-1"), List.of(),
            "static-v1", List.of(new PreviewInputFile("main", "config/main.txt", content)),
            ExistingState.UNKNOWN, List.of());
    }

    @Test
    void createsOnlyMetadataThatCanAuthorizeExactRegeneration() {
        var input = input("safe config\n");
        var result = PreviewFingerprint.compute(input);
        var preview = new StoredPreviewState("preview-1", BASIS, result.fingerprint(),
            NOW.plusSeconds(600));
        var approval = PreviewApprovalIssuer.issueStoredReady(preview, BASIS,
            "preview-1", result.fingerprint(),
            true, "approval-1", CLOCK);
        assertEquals("preview-1", approval.previewId());
        assertEquals(result.fingerprint(), approval.fingerprint());
        assertEquals(NOW, approval.approvedAt());
        assertEquals(preview.expiresAt(), approval.expiresAt());
        var generated = ApprovedConfigurationGenerator.generate(preview, approval, BASIS,
            "approval-1", "preview-1", result.fingerprint(), input, "generation-1", CLOCK);
        assertTrue(generated.zipBytes().length > 0);
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> ApprovedConfigurationGenerator.generate(preview, approval, BASIS,
                "approval-1", "preview-1", result.fingerprint(), input("changed\n"),
                "generation-2", CLOCK));
    }

    @Test
    void deniesMissingConfirmationExpiredPreviewAndChangedBasis() {
        var result = PreviewFingerprint.compute(input("safe config\n"));
        var preview = new StoredPreviewState("preview-1", BASIS, result.fingerprint(),
            NOW.plusSeconds(600));
        assertThrows(PreviewFreshnessGate.InvalidPreviewStateException.class,
            () -> PreviewApprovalIssuer.issueStoredReady(preview, BASIS,
                "preview-1", result.fingerprint(),
                false, "approval-1", CLOCK));
        assertThrows(PreviewFreshnessGate.ExpiredPreviewException.class,
            () -> PreviewApprovalIssuer.issueStoredReady(new StoredPreviewState("preview-1", BASIS,
                result.fingerprint(), NOW), BASIS, "preview-1", result.fingerprint(),
                true, "approval-1", CLOCK));
        var changed = new PreviewBasis("project-1", 2, "profile-1", 3,
            "event-1", 4, 5, 7, "catalog-1", "a".repeat(64));
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> PreviewApprovalIssuer.issueStoredReady(preview, changed,
                "preview-1", result.fingerprint(),
                true, "approval-1", CLOCK));
    }

    @Test
    void approvesStoredReadyPreviewWithoutUnavailableOriginalFileContent() {
        var result = PreviewFingerprint.compute(input("safe config\n"));
        var preview = new StoredPreviewState("preview-1", BASIS, result.fingerprint(),
            NOW.plusSeconds(600));

        var approval = PreviewApprovalIssuer.issueStoredReady(preview, BASIS,
            "preview-1", result.fingerprint(), true, "approval-1", CLOCK);

        assertEquals(preview.previewId(), approval.previewId());
        assertEquals(preview.fingerprint(), approval.fingerprint());
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> PreviewApprovalIssuer.issueStoredReady(preview, BASIS,
                "preview-1", "f".repeat(64), true, "approval-2", CLOCK));
        assertThrows(PreviewFreshnessGate.InvalidPreviewStateException.class,
            () -> PreviewApprovalIssuer.issueStoredReady(preview, BASIS,
                "preview-1", result.fingerprint(), false, "approval-2", CLOCK));
        var changed = new PreviewBasis("project-1", 2, "profile-1", 3,
            "event-1", 4, 5, 7, "catalog-1", "a".repeat(64));
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> PreviewApprovalIssuer.issueStoredReady(preview, changed,
                "preview-1", result.fingerprint(), true, "approval-2", CLOCK));
        assertThrows(PreviewFreshnessGate.ExpiredPreviewException.class,
            () -> PreviewApprovalIssuer.issueStoredReady(new StoredPreviewState("preview-1",
                BASIS, result.fingerprint(), NOW), BASIS, "preview-1", result.fingerprint(),
                true, "approval-2", CLOCK));
    }
}
