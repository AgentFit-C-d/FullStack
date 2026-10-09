package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogReadyPreviewApprovalWorkflowTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void issuesStoredApprovalAfterCheckingActiveCatalogWithoutPreviewContents() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        PreviewBasis basis = basis(hash);
        StoredPreviewState preview = preview(basis);

        var approval = CatalogReadyPreviewApprovalWorkflow.issue(directory, hash,
            preview, basis, FINGERPRINT, true, "approval-1", CLOCK);

        assertEquals("approval-1", approval.approvalId());
        assertEquals(FINGERPRINT, approval.fingerprint());
    }

    @Test
    void rejectsCatalogChangeOrWrongActiveReleaseBeforeApproval() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        PreviewBasis basis = basis(hash);
        StoredPreviewState preview = preview(basis);

        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogReadyPreviewApprovalWorkflow.issue(directory, "f".repeat(64),
                preview, basis, FINGERPRINT, true, "approval-1", CLOCK));
        PreviewBasis wrongRelease = new PreviewBasis("project-1", 1, "profile-1", 1,
            "event-1", 0, 0, 1, "other-release", hash);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogReadyPreviewApprovalWorkflow.issue(directory, hash,
                preview(wrongRelease), wrongRelease, FINGERPRINT, true, "approval-1", CLOCK));
        Files.writeString(directory.resolve("templates/main.txt"), "changed\n");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogReadyPreviewApprovalWorkflow.issue(directory, hash,
                preview, basis, FINGERPRINT, true, "approval-1", CLOCK));
    }

    private static PreviewBasis basis(String hash) {
        return new PreviewBasis("project-1", 1, "profile-1", 1, "event-1",
            0, 0, 1, "synthetic-recommendation", hash);
    }

    private static StoredPreviewState preview(PreviewBasis basis) {
        return new StoredPreviewState("preview-1", basis, FINGERPRINT, NOW.plusSeconds(600));
    }
}
