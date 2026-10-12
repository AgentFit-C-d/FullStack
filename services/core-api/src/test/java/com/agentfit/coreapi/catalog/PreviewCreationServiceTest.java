package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision.Status;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class PreviewCreationServiceTest {
    @TempDir Path directory;

    @Test
    void savesOnlyContentFreeMetadataAfterVerifiedPreviewAssembly() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = request(hash);
        AtomicInteger saves = new AtomicInteger();
        var service = new PreviewCreationService(
            (projectId, recommendationId) -> context(request),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            (context, draft) -> {
                assertEquals("project-1", context.currentBasis().projectId());
                assertEquals("rec-1", draft.recommendationId());
                assertEquals(List.of("example-tool"), draft.selectedToolKeys());
                assertEquals(ExistingState.UNKNOWN, draft.existingState());
                assertEquals(1, draft.fileMetadata().size());
                var file = draft.fileMetadata().getFirst();
                assertEquals("config/main.txt", file.relativePath());
                assertEquals("main", file.targetKey());
                assertNull(file.beforeHash());
                assertEquals(64, file.afterHash().length());
                assertFalse(draft.toString().contains("literal config"));
                assertEquals(64, draft.fingerprint().length());
                saves.incrementAndGet();
                return new StoredPreviewState("preview-1", draft.basis(), draft.fingerprint(),
                    Instant.parse("2026-10-10T11:50:00Z"),
                    Instant.parse("2026-10-10T12:00:00Z"));
            });

        var created = service.create("project-1", request);

        assertEquals("preview-1", created.saved().previewId());
        assertEquals("literal config\n", created.preview().files().getFirst().content());
        assertEquals(created.preview().fingerprint(), created.saved().fingerprint());
        assertTrue(created.coverage().complete());
        assertEquals(List.of(), created.coverage().unmetCapabilityKeys());
        assertEquals(1, saves.get());
    }

    @Test
    void savesPartialChoiceAndReturnsUnmetRequiredCapability() throws Exception {
        String hash = SyntheticCatalogBundle.writeWithDistinctFallbackCapability(directory);
        var request = request(hash);
        var offered = new StoredRecommendationState("rec-1", request.basis(), Status.RECOMMENDED,
            List.of("example-tool", "fallback-tool"));
        var service = new PreviewCreationService(
            (projectId, recommendationId) -> new PreviewCreationService.TrustedContext(
                request.basis(), request.target(), offered, Map.of(),
                Set.of("cap_document_reference", "cap_browser_verification")),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            (trusted, draft) -> {
                assertEquals(List.of("example-tool"), draft.selectedToolKeys());
                return new StoredPreviewState("preview-1", draft.basis(),
                    draft.fingerprint(), Instant.parse("2026-10-10T11:50:00Z"),
                    Instant.parse("2026-10-10T12:00:00Z"));
            });

        var created = service.create("project-1", request);

        assertEquals(List.of("cap_browser_verification"), created.coverage().unmetCapabilityKeys());
        assertFalse(created.coverage().complete());
    }

    @Test
    void refusesForeignOwnerCheckedContextBeforeCatalogOrStore() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = request(hash);
        var foreign = new PreviewCreationService.TrustedContext(
            new PreviewBasis("other-project", 1, "profile-1", 1, "event-1", 0, 0, 1,
                "synthetic-recommendation", hash), request.target(), context(request).recommendation(),
            Map.of(), Set.of("cap_document_reference"));
        AtomicInteger catalogReads = new AtomicInteger();
        var service = new PreviewCreationService(
            (projectId, recommendationId) -> foreign,
            () -> { catalogReads.incrementAndGet(); return null; },
            (context, draft) -> fail("foreign project must not be saved"));

        assertThrows(IllegalArgumentException.class, () -> service.create("project-1", request));
        assertEquals(0, catalogReads.get());
    }

    @Test
    void propagatesConcurrentSaveFailureWithoutReturningPreview() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = request(hash);
        var service = new PreviewCreationService(
            (projectId, recommendationId) -> context(request),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            (context, draft) -> { throw new RecommendationCreationService.SnapshotChangedException(); });

        assertThrows(RecommendationCreationService.SnapshotChangedException.class,
            () -> service.create("project-1", request));
    }

    @Test
    void refusesStoredPreviewWithInvalidServerTimestamps() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = request(hash);
        var service = new PreviewCreationService(
            (projectId, recommendationId) -> context(request),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            (trusted, draft) -> new StoredPreviewState("preview-1", draft.basis(),
                draft.fingerprint(), Instant.parse("2026-10-10T12:00:00Z"),
                Instant.parse("2026-10-10T11:50:00Z")));

        assertThrows(IllegalStateException.class, () -> service.create("project-1", request));
    }

    @Test
    void forwardsTrustedInstalledDependencyWithoutAddingItToGeneratedFiles() throws Exception {
        String hash = SyntheticCatalogBundle.writeWithInstalledDependency(directory);
        var request = request(hash);
        var service = new PreviewCreationService(
            (projectId, recommendationId) -> new PreviewCreationService.TrustedContext(
                request.basis(), request.target(), context(request).recommendation(),
                Map.of("runtime", "1.0"), Set.of("cap_document_reference")),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            (trusted, draft) -> new StoredPreviewState("preview-1", draft.basis(),
                draft.fingerprint(), Instant.parse("2026-10-10T11:50:00Z"),
                Instant.parse("2026-10-10T12:00:00Z")));

        var preview = service.create("project-1", request);
        assertEquals(List.of("config/main.txt"), preview.preview().files().stream()
            .map(file -> file.relativePath()).toList());
    }

    private static PreviewCreationService.TrustedContext context(CatalogPreviewRequest request) {
        return new PreviewCreationService.TrustedContext(request.basis(), request.target(),
            new StoredRecommendationState("rec-1", request.basis(), Status.RECOMMENDED,
                List.of("example-tool")), Map.of(), Set.of("cap_document_reference"));
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
