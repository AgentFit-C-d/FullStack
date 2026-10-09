package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision.Status;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogPreviewApprovalWorkflowTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final EnvironmentTarget TARGET = new EnvironmentTarget("WINDOWS", "example-client", "1.0");

    @Test
    void issuesApprovalOnlyForCurrentCatalogRenderedPreviewAndExplicitConfirmation() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        CatalogPreviewRequest request = request(hash, PermissionPolicy.ASK_EACH_TIME);
        StoredRecommendationState recommendation = recommendation(request);
        PreviewFingerprintResult previewResult = CatalogPreviewAssembler.assemble(directory, hash,
            recommendation, request.basis(), TARGET, request);
        StoredPreviewState preview = new StoredPreviewState("preview-1", request.basis(),
            previewResult.fingerprint(), NOW.plusSeconds(600));

        StoredApprovalState approval = CatalogPreviewApprovalWorkflow.issue(directory, hash,
            recommendation, request.basis(), TARGET, request, preview, previewResult.fingerprint(),
            true, "approval-1", CLOCK);

        assertEquals("approval-1", approval.approvalId());
        assertEquals(preview.fingerprint(), approval.fingerprint());
        assertEquals(NOW, approval.approvedAt());
        assertEquals(preview.expiresAt(), approval.expiresAt());
        assertThrows(IllegalArgumentException.class,
            () -> CatalogPreviewApprovalWorkflow.issue(directory, hash, recommendation,
                request.basis(), TARGET, request, preview, previewResult.fingerprint(),
                false, "approval-2", CLOCK));
    }

    @Test
    void rejectsChangedPolicyTargetOrCatalogBeforeApproval() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        CatalogPreviewRequest request = request(hash, PermissionPolicy.ASK_EACH_TIME);
        StoredRecommendationState recommendation = recommendation(request);
        PreviewFingerprintResult result = CatalogPreviewAssembler.assemble(directory, hash,
            recommendation, request.basis(), TARGET, request);
        StoredPreviewState preview = new StoredPreviewState("preview-1", request.basis(),
            result.fingerprint(), NOW.plusSeconds(600));

        assertThrows(IllegalStateException.class,
            () -> CatalogPreviewApprovalWorkflow.issue(directory, hash, recommendation,
                request.basis(), TARGET, request(hash, PermissionPolicy.DENY), preview,
                result.fingerprint(), true, "approval-1", CLOCK));
        assertThrows(CatalogPreviewAssembler.InvalidAssemblyException.class,
            () -> CatalogPreviewApprovalWorkflow.issue(directory, hash, recommendation,
                request.basis(), new EnvironmentTarget("MACOS", "example-client", "1.0"),
                request, preview, result.fingerprint(), true, "approval-1", CLOCK));
        Files.writeString(directory.resolve("templates/main.txt"), "changed config\n");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogPreviewApprovalWorkflow.issue(directory, hash, recommendation,
                request.basis(), TARGET, request, preview, result.fingerprint(),
                true, "approval-1", CLOCK));
    }

    private static CatalogPreviewRequest request(String hash, PermissionPolicy policy) {
        return new CatalogPreviewRequest(new PreviewBasis("project-1", 1,
            "profile-1", 1, "event-1", 0, 0, 1, "synthetic-recommendation", hash),
            "rec-1", TARGET, List.of("example-tool"),
            List.of(new PermissionSelection("example-tool", "read", policy)),
            ExistingState.UNKNOWN, List.of(), "static-v1");
    }

    private static StoredRecommendationState recommendation(CatalogPreviewRequest request) {
        return new StoredRecommendationState("rec-1", request.basis(), Status.RECOMMENDED,
            List.of("example-tool"));
    }
}
