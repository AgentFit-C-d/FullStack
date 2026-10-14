package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision.Status;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationDownloadServiceTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void returnsApprovedZipOnlyAfterContentFreeHistoryIsSaved() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = request(hash);
        var context = context(hash, request);
        AtomicInteger saves = new AtomicInteger();
        var service = new ConfigurationDownloadService(
            (projectId, approvalId) -> context,
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            () -> "generation-1", CLOCK,
            (trusted, history) -> {
                assertEquals("project-1", trusted.currentBasis().projectId());
                assertEquals("generation-1", history.id());
                assertEquals(List.of("config/main.txt"), history.files().stream()
                    .map(file -> file.relativePath()).toList());
                saves.incrementAndGet();
                return history.id();
            });

        var downloaded = service.download("project-1", "approval-1", "preview-1",
            context.preview().fingerprint(), request);

        assertEquals("generation-1", downloaded.history().id());
        try (ZipInputStream zip = new ZipInputStream(
            new ByteArrayInputStream(downloaded.zipBytes()))) {
            assertEquals("config/main.txt", zip.getNextEntry().getName());
            assertEquals("literal config\n", new String(zip.readAllBytes()));
        }
        assertEquals(1, saves.get());
    }

    @Test
    void rejectsChangedStoredRecommendationEvidenceBeforeHistoryOrZip() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = request(hash);
        var original = context(hash, request);
        for (var changed : List.of(
            new PreviewCreationService.StoredItemEvidence("example-tool", "2.0", "support-1", true),
            new PreviewCreationService.StoredItemEvidence("example-tool", "1.0", "other-support", true),
            new PreviewCreationService.StoredItemEvidence("example-tool", "1.0", "support-1", false))) {
            var context = new ConfigurationDownloadService.TrustedContext(
                original.currentBasis(), original.currentTarget(), original.recommendation(),
                original.preview(), original.approval(), original.installedToolVersions(),
                List.of(changed));
            var service = new ConfigurationDownloadService((projectId, approvalId) -> context,
                () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
                () -> "generation-1", CLOCK,
                (trusted, history) -> fail("drifted recommendation must not save history"));
            assertThrows(IllegalArgumentException.class,
                () -> service.download("project-1", "approval-1", "preview-1",
                    original.preview().fingerprint(), request));
        }
    }

    @Test
    void historySaveConflictPreventsDownloadSuccess() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = request(hash);
        var service = new ConfigurationDownloadService(
            (projectId, approvalId) -> context(hash, request),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            () -> "generation-1", CLOCK,
            (trusted, history) -> { throw new RecommendationCreationService.SnapshotChangedException(); });

        assertThrows(RecommendationCreationService.SnapshotChangedException.class,
            () -> service.download("project-1", "approval-1", "preview-1",
                context(hash, request).preview().fingerprint(), request));
    }

    @Test
    void refusesForeignProjectBeforeCatalogOrHistorySave() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = request(hash);
        var context = context(hash, request);
        var foreignBasis = new PreviewBasis("other-project", 1, "profile-1", 1,
            "event-1", 0, 0, 1, "synthetic-recommendation", hash);
        var foreign = new ConfigurationDownloadService.TrustedContext(foreignBasis,
            context.currentTarget(), context.recommendation(), context.preview(), context.approval(),
            context.installedToolVersions(), context.storedItems());
        AtomicInteger catalogReads = new AtomicInteger();
        var service = new ConfigurationDownloadService(
            (projectId, approvalId) -> foreign,
            () -> { catalogReads.incrementAndGet(); return null; },
            () -> "generation-1", CLOCK,
            (trusted, history) -> fail("foreign history must not be saved"));

        assertThrows(IllegalArgumentException.class,
            () -> service.download("project-1", "approval-1", "preview-1",
                context.preview().fingerprint(), request));
        assertEquals(0, catalogReads.get());
    }

    @Test
    void regeneratesWithCurrentTrustedInstalledDependencyAndRejectsVersionDrift() throws Exception {
        String hash = SyntheticCatalogBundle.writeWithInstalledDependency(directory);
        var request = request(hash);
        var original = contextWithInstalled(hash, request, "1.0");
        var service = new ConfigurationDownloadService(
            (projectId, approvalId) -> original,
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            () -> "generation-1", CLOCK, (trusted, history) -> history.id());
        var generated = service.download("project-1", "approval-1", "preview-1",
            original.preview().fingerprint(), request);
        assertEquals(List.of("config/main.txt"), generated.history().files().stream()
            .map(file -> file.relativePath()).toList());

        var changed = new ConfigurationDownloadService(
            (projectId, approvalId) -> contextWithInstalled(hash, request, "0.9",
                original.preview(), original.approval()),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            () -> "generation-2", CLOCK,
            (trusted, history) -> fail("changed dependency must not produce history"));
        assertThrows(IllegalArgumentException.class,
            () -> changed.download("project-1", "approval-1", "preview-1",
                original.preview().fingerprint(), request));
    }

    private ConfigurationDownloadService.TrustedContext context(String hash,
                                                                  CatalogPreviewRequest request) {
        var rec = new StoredRecommendationState("rec-1", request.basis(), Status.RECOMMENDED,
            List.of("example-tool"));
        var fingerprint = CatalogPreviewAssembler.assemble(directory, hash, rec, request.basis(),
            request.target(), request).fingerprint();
        var preview = new StoredPreviewState("preview-1", request.basis(), fingerprint,
            NOW.minusSeconds(120), NOW.plusSeconds(600));
        var approval = new StoredApprovalState("approval-1", "preview-1", fingerprint,
            NOW.minusSeconds(60), NOW.plusSeconds(600));
        return new ConfigurationDownloadService.TrustedContext(request.basis(), request.target(),
            rec, preview, approval, Map.of(), evidence());
    }

    private ConfigurationDownloadService.TrustedContext contextWithInstalled(String hash,
        CatalogPreviewRequest request, String version) {
        var rec = new StoredRecommendationState("rec-1", request.basis(), Status.RECOMMENDED,
            List.of("example-tool"));
        var fingerprint = CatalogPreviewAssembler.assemble(directory, hash, rec, request.basis(),
            request.target(), request, Map.of("runtime", "1.0")).fingerprint();
        var preview = new StoredPreviewState("preview-1", request.basis(), fingerprint,
            NOW.minusSeconds(120), NOW.plusSeconds(600));
        var approval = new StoredApprovalState("approval-1", "preview-1", fingerprint,
            NOW.minusSeconds(60), NOW.plusSeconds(600));
        return contextWithInstalled(hash, request, version, preview, approval);
    }

    private ConfigurationDownloadService.TrustedContext contextWithInstalled(String hash,
        CatalogPreviewRequest request, String version, StoredPreviewState preview,
        StoredApprovalState approval) {
        var rec = new StoredRecommendationState("rec-1", request.basis(), Status.RECOMMENDED,
            List.of("example-tool"));
        return new ConfigurationDownloadService.TrustedContext(request.basis(), request.target(),
            rec, preview, approval, Map.of("runtime", version), evidence());
    }

    private static List<PreviewCreationService.StoredItemEvidence> evidence() {
        return List.of(new PreviewCreationService.StoredItemEvidence(
            "example-tool", "1.0", "support-1", true));
    }

    private static CatalogPreviewRequest request(String hash) {
        var basis = new PreviewBasis("project-1", 1, "profile-1", 1, "event-1", 0, 0, 1,
            "synthetic-recommendation", hash);
        return new CatalogPreviewRequest(basis, "rec-1",
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
            List.of("example-tool"),
            List.of(new PermissionSelection("example-tool", "read", PermissionPolicy.ASK_EACH_TIME)),
            ExistingState.UNKNOWN, List.of(), "static-v1");
    }
}
