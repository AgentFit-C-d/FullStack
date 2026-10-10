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
                assertEquals(64, draft.fingerprint().length());
                saves.incrementAndGet();
                return new StoredPreviewState("preview-1", draft.basis(), draft.fingerprint(),
                    Instant.parse("2026-10-10T12:00:00Z"));
            });

        var created = service.create("project-1", request);

        assertEquals("preview-1", created.saved().previewId());
        assertEquals("literal config\n", created.preview().files().getFirst().content());
        assertEquals(created.preview().fingerprint(), created.saved().fingerprint());
        assertEquals(1, saves.get());
    }

    @Test
    void refusesForeignOwnerCheckedContextBeforeCatalogOrStore() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = request(hash);
        var foreign = new PreviewCreationService.TrustedContext(
            new PreviewBasis("other-project", 1, "profile-1", 1, "event-1", 0, 0, 1,
                "synthetic-recommendation", hash), request.target(), context(request).recommendation());
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

    private static PreviewCreationService.TrustedContext context(CatalogPreviewRequest request) {
        return new PreviewCreationService.TrustedContext(request.basis(), request.target(),
            new StoredRecommendationState("rec-1", request.basis(), Status.RECOMMENDED,
                List.of("example-tool")));
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
