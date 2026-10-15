package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.export.PreviewFreshnessGate;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalCreationServiceTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void returnsApprovalOnlyAfterAtomicOwnerCheckedSave() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var context = context(hash);
        AtomicInteger saves = new AtomicInteger();
        var service = new ApprovalCreationService(
            (projectId, previewId) -> context,
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            () -> "approval-1", CLOCK,
            (trusted, issued) -> {
                assertEquals("project-1", trusted.currentBasis().projectId());
                assertEquals("preview-1", issued.previewId());
                assertEquals(NOW, issued.approvedAt());
                saves.incrementAndGet();
                return issued;
            });

        StoredApprovalState approved = service.approve("project-1", "preview-1",
            "b".repeat(64), true);

        assertEquals("approval-1", approved.approvalId());
        assertEquals(1, saves.get());
    }

    @Test
    void refusesMissingConfirmationOrStalePreviewWithoutSave() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var service = new ApprovalCreationService(
            (projectId, previewId) -> context(hash),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            () -> "approval-1", CLOCK,
            (trusted, issued) -> fail("invalid approval must not be saved"));

        assertThrows(PreviewFreshnessGate.InvalidPreviewStateException.class,
            () -> service.approve("project-1", "preview-1", "b".repeat(64), false));
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> service.approve("project-1", "preview-1", "c".repeat(64), true));
    }

    @Test
    void propagatesConcurrentSaveConflictInsteadOfReportingApproval() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var service = new ApprovalCreationService(
            (projectId, previewId) -> context(hash),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            () -> "approval-1", CLOCK,
            (trusted, issued) -> { throw new RecommendationCreationService.SnapshotChangedException(); });

        assertThrows(RecommendationCreationService.SnapshotChangedException.class,
            () -> service.approve("project-1", "preview-1", "b".repeat(64), true));
    }

    private static ApprovalCreationService.TrustedContext context(String hash) {
        var basis = new PreviewBasis("project-1", 1, "profile-1", 1, "event-1",
            0, 0, 1, "synthetic-recommendation", hash);
        return new ApprovalCreationService.TrustedContext(basis,
            new StoredPreviewState("preview-1", basis, "b".repeat(64),
                NOW.minusSeconds(60), NOW.plusSeconds(600)));
    }
}
