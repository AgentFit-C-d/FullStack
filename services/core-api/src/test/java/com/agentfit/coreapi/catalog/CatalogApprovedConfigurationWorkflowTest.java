package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.export.ApprovedPreviewZipExporter;
import com.agentfit.coreapi.configuration.export.PreviewFreshnessGate;
import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision.Status;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogApprovedConfigurationWorkflowTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final EnvironmentTarget TARGET = new EnvironmentTarget("WINDOWS", "example-client", "1.0");

    @Test
    void generatesZipAndHistoryOnlyFromReverifiedCatalogAndCurrentApproval() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        CatalogPreviewRequest request = request(hash, PermissionPolicy.ASK_EACH_TIME);
        StoredRecommendationState recommendation = recommendation(request);
        PreviewFingerprintResult previewResult = CatalogPreviewAssembler.assemble(directory, hash,
            recommendation, request.basis(), TARGET, request);
        StoredPreviewState preview = new StoredPreviewState("preview-1", request.basis(),
            previewResult.fingerprint(), NOW.plusSeconds(600));
        StoredApprovalState approval = CatalogReadyPreviewApprovalWorkflow.issue(directory, hash,
            preview, request.basis(), previewResult.fingerprint(), true, "approval-1", CLOCK);

        var generated = CatalogApprovedConfigurationWorkflow.generate(directory, hash, recommendation,
            request.basis(), TARGET, request, preview, approval, "approval-1",
            "preview-1", previewResult.fingerprint(), "generation-1", CLOCK);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(generated.zipBytes()),
            StandardCharsets.UTF_8)) {
            assertEquals("config/main.txt", zip.getNextEntry().getName());
            assertArrayEquals("literal config\n".getBytes(StandardCharsets.UTF_8), zip.readAllBytes());
            assertNull(zip.getNextEntry());
        }
        assertEquals(ConfigurationGenerationHistory.Status.GENERATED, generated.history().status());
        assertEquals(previewResult.fingerprint(), generated.history().fingerprint());
    }

    @Test
    void refusesChangedCatalogBytesAndChangedPermissionAfterApproval() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        CatalogPreviewRequest request = request(hash, PermissionPolicy.ASK_EACH_TIME);
        StoredRecommendationState recommendation = recommendation(request);
        PreviewFingerprintResult result = CatalogPreviewAssembler.assemble(directory, hash,
            recommendation, request.basis(), TARGET, request);
        StoredPreviewState preview = new StoredPreviewState("preview-1", request.basis(),
            result.fingerprint(), NOW.plusSeconds(600));
        StoredApprovalState approval = CatalogReadyPreviewApprovalWorkflow.issue(directory, hash,
            preview, request.basis(), result.fingerprint(), true, "approval-1", CLOCK);

        assertThrows(IllegalStateException.class, () -> CatalogApprovedConfigurationWorkflow.generate(
            directory, hash, recommendation, request.basis(), TARGET,
            request(hash, PermissionPolicy.DENY), preview, approval, "approval-1",
            "preview-1", result.fingerprint(), "generation-1", CLOCK));
        Files.writeString(directory.resolve("templates/main.txt"), "changed config\n");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogApprovedConfigurationWorkflow.generate(directory, hash, recommendation,
                request.basis(), TARGET, request, preview, approval, "approval-1",
                "preview-1", result.fingerprint(), "generation-1", CLOCK));
    }

    @Test
    void requiresSameTransientOriginalFileAtExportWithoutStoringItInApproval() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        CatalogPreviewRequest base = request(hash, PermissionPolicy.ASK_EACH_TIME);
        PreviewInputFile original = new PreviewInputFile("main", "config/main.txt", "previous config\n");
        CatalogPreviewRequest request = new CatalogPreviewRequest(base.basis(), base.recommendationId(),
            base.target(), base.selectedToolKeys(), base.permissionSelections(),
            ExistingState.PROVIDED, List.of(original), base.generatorVersion());
        StoredRecommendationState recommendation = recommendation(request);
        PreviewFingerprintResult result = CatalogPreviewAssembler.assemble(directory, hash,
            recommendation, request.basis(), TARGET, request);
        StoredPreviewState preview = new StoredPreviewState("preview-1", request.basis(),
            result.fingerprint(), NOW.plusSeconds(600));
        StoredApprovalState approval = CatalogReadyPreviewApprovalWorkflow.issue(directory, hash,
            preview, request.basis(), result.fingerprint(), true, "approval-1", CLOCK);

        var generated = CatalogApprovedConfigurationWorkflow.generate(directory, hash, recommendation,
            request.basis(), TARGET, request, preview, approval, "approval-1",
            "preview-1", result.fingerprint(), "generation-1", CLOCK);
        assertEquals("generation-1", generated.history().id());
        CatalogPreviewRequest changedOriginal = new CatalogPreviewRequest(base.basis(), base.recommendationId(),
            base.target(), base.selectedToolKeys(), base.permissionSelections(), ExistingState.PROVIDED,
            List.of(new PreviewInputFile("main", "config/main.txt", "altered original\n")),
            base.generatorVersion());
        assertThrows(IllegalStateException.class,
            () -> CatalogApprovedConfigurationWorkflow.generate(directory, hash, recommendation,
                request.basis(), TARGET, changedOriginal, preview, approval,
                "approval-1", "preview-1", result.fingerprint(), "generation-2", CLOCK));
    }

    @Test
    void rejectsSubmittedPreviewIdentityOrFingerprintThatDiffersFromStoredApproval() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        CatalogPreviewRequest request = request(hash, PermissionPolicy.ASK_EACH_TIME);
        StoredRecommendationState recommendation = recommendation(request);
        PreviewFingerprintResult result = CatalogPreviewAssembler.assemble(directory, hash,
            recommendation, request.basis(), TARGET, request);
        StoredPreviewState preview = new StoredPreviewState("preview-1", request.basis(),
            result.fingerprint(), NOW.plusSeconds(600));
        StoredApprovalState approval = CatalogReadyPreviewApprovalWorkflow.issue(directory, hash,
            preview, request.basis(), result.fingerprint(), true, "approval-1", CLOCK);

        assertThrows(ApprovedPreviewZipExporter.InvalidApprovalException.class,
            () -> CatalogApprovedConfigurationWorkflow.generate(directory, hash, recommendation,
                request.basis(), TARGET, request, preview, approval, "approval-1",
                "other-preview", result.fingerprint(), "generation-1", CLOCK));
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> CatalogApprovedConfigurationWorkflow.generate(directory, hash, recommendation,
                request.basis(), TARGET, request, preview, approval, "approval-1",
                "preview-1", "f".repeat(64), "generation-1", CLOCK));
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
