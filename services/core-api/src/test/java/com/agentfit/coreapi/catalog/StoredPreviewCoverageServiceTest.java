package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.PreviewFreshnessGate;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationContextReader;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class StoredPreviewCoverageServiceTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
    private static final EnvironmentTarget TARGET =
        new EnvironmentTarget("WINDOWS", "example-client", "1.0");

    @Test
    void readsPartialWarningFromOwnedSavedPreviewAndApprovedCatalog() throws Exception {
        String hash = SyntheticCatalogBundle.writeWithDistinctFallbackCapability(directory);
        var service = new StoredPreviewCoverageService(
            (projectId, previewId) -> context(hash, "project-1", NOW.plusSeconds(600)),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(List.of("cap_browser_verification"),
            service.coverage("project-1", "preview-1").unmetCapabilityKeys());
    }

    @Test
    void refusesForeignOrExpiredPreviewBeforeCatalogRead() throws Exception {
        String hash = SyntheticCatalogBundle.writeWithDistinctFallbackCapability(directory);
        AtomicInteger catalogReads = new AtomicInteger();
        var foreign = new StoredPreviewCoverageService(
            (projectId, previewId) -> context(hash, "other-project", NOW.plusSeconds(600)),
            () -> { catalogReads.incrementAndGet(); return null; },
            Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class,
            () -> foreign.coverage("project-1", "preview-1"));

        var expired = new StoredPreviewCoverageService(
            (projectId, previewId) -> context(hash, "project-1", NOW),
            () -> { catalogReads.incrementAndGet(); return null; },
            Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(PreviewFreshnessGate.ExpiredPreviewException.class,
            () -> expired.coverage("project-1", "preview-1"));

        var missingTarget = new StoredPreviewCoverageService(
            (projectId, previewId) -> {
                var valid = context(hash, "project-1", NOW.plusSeconds(600));
                return new StoredPreviewCoverageService.TrustedContext(
                    valid.currentBasis(), null, Map.of(), valid.saved());
            },
            () -> { catalogReads.incrementAndGet(); return null; },
            Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class,
            () -> missingTarget.coverage("project-1", "preview-1"));
        assertEquals(0, catalogReads.get());
    }

    @Test
    void refusesUnapprovedCatalogBeforeShowingSavedWarning() throws Exception {
        String hash = SyntheticCatalogBundle.writeWithDistinctFallbackCapability(directory);
        var service = new StoredPreviewCoverageService(
            (projectId, previewId) -> context(hash, "project-1", NOW.plusSeconds(600)),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, "c".repeat(64)),
            Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> service.coverage("project-1", "preview-1"));
    }

    private static StoredPreviewCoverageService.TrustedContext context(
        String hash, String projectId, Instant expiresAt) {
        var basis = new PreviewBasis(projectId, 1, "profile-1", 1, "event-1", 0, 0, 1,
            "synthetic-recommendation", hash);
        var recommendation = new StoredRecommendationState("rec-1", basis,
            RecommendationDecision.Status.RECOMMENDED,
            List.of("example-tool", "fallback-tool"));
        var preview = new StoredPreviewState("preview-1", basis, "b".repeat(64),
            NOW.minusSeconds(60), expiresAt);
        var saved = new JdbcConfigurationContextReader.Snapshot(recommendation, preview,
            null, List.of(), Set.of("cap_document_reference", "cap_browser_verification"),
            List.of("example-tool"));
        return new StoredPreviewCoverageService.TrustedContext(basis, TARGET, Map.of(), saved);
    }
}
