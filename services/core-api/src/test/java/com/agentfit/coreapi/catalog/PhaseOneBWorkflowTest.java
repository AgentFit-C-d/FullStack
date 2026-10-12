package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryQueryService;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReportService;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Test-only A/AI ports exercise B's complete internal handoff; no product Catalog or DB. */
class PhaseOneBWorkflowTest {
    @TempDir Path directory;
    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final EnvironmentTarget TARGET =
        new EnvironmentTarget("WINDOWS", "example-client", "1.0");

    @Test
    void recommendationThroughPreviewApprovalDownloadAndUserReportKeepsOneBasis() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var catalog = new RecommendationCreationService.ApprovedCatalog(directory, hash);
        var snapshot = new RecommendationCreationService.OwnerCheckedSnapshot(
            "project-1", 7, "profile-1", 3, "event-1", 2, 4, 5,
            Set.of("profile.features"), Map.of("profile.features", "[\"document search\"]"),
            false, TARGET, Map.of());
        AtomicReference<StoredRecommendationState> recommendation = new AtomicReference<>();
        var recommendationService = new RecommendationCreationService(
            projectId -> snapshot,
            current -> new RecommendationCreationService.CapabilityAssessment(
                CapabilityKey.keys().stream().sorted().map(key -> new AiCapabilityIntake.Claim(
                    key, key.equals("cap_document_reference")
                        ? AiCapabilityIntake.Need.REQUIRED : AiCapabilityIntake.Need.OPTIONAL,
                    "Confirmed profile", List.of("profile.features"))).toList(), List.of()),
            () -> catalog,
            (current, basis, result) -> {
                recommendation.set(new StoredRecommendationState("rec-1", basis,
                    result.decision().status(), result.decision().toolKeys()));
                return "rec-1";
            });

        var created = recommendationService.create("project-1",
            new RecommendationCreationService.ExpectedVersions(7, 4, 5));
        assertEquals(RecommendationDecision.Status.RECOMMENDED,
            created.result().decision().status());
        var request = new CatalogPreviewRequest(created.basis(), created.recommendationId(),
            TARGET, List.of("example-tool"),
            List.of(new PermissionSelection("example-tool", "read",
                PermissionPolicy.ASK_EACH_TIME)), ExistingState.UNKNOWN, List.of(), "static-v1");

        AtomicReference<StoredPreviewState> storedPreview = new AtomicReference<>();
        var previewService = new PreviewCreationService(
            (projectId, recommendationId) -> new PreviewCreationService.TrustedContext(
                created.basis(), TARGET, recommendation.get(), Map.of(),
                new PreviewCreationService.RequiredCapabilities(created.recommendationId(),
                    created.basis(), Set.of("cap_document_reference")),
                List.of(new PreviewCreationService.StoredItemEvidence(
                    "example-tool", "1.0", "support-1", true))),
            () -> catalog,
            (context, draft) -> {
                var saved = new StoredPreviewState("preview-1", draft.basis(),
                    draft.fingerprint(), NOW.minusSeconds(60), NOW.plusSeconds(600));
                storedPreview.set(saved);
                return saved;
            });
        var preview = previewService.create("project-1", request);
        assertEquals("literal config\n", preview.preview().files().getFirst().content());

        AtomicReference<StoredApprovalState> storedApproval = new AtomicReference<>();
        var approvalService = new ApprovalCreationService(
            (projectId, previewId) -> new ApprovalCreationService.TrustedContext(
                created.basis(), storedPreview.get()),
            () -> catalog, () -> "approval-1", CLOCK,
            (context, issued) -> { storedApproval.set(issued); return issued; });
        var approval = approvalService.approve("project-1", "preview-1",
            preview.saved().fingerprint(), true);

        AtomicReference<ConfigurationGenerationHistory> storedHistory = new AtomicReference<>();
        var downloadService = new ConfigurationDownloadService(
            (projectId, approvalId) -> new ConfigurationDownloadService.TrustedContext(
                created.basis(), TARGET, recommendation.get(), storedPreview.get(),
                storedApproval.get(), Map.of(),
                List.of(new PreviewCreationService.StoredItemEvidence(
                    "example-tool", "1.0", "support-1", true))),
            () -> catalog, () -> "generation-1", CLOCK,
            (context, history) -> { storedHistory.set(history); return history.id(); });
        var download = downloadService.download("project-1", approval.approvalId(),
            preview.saved().previewId(), preview.saved().fingerprint(), request);
        try (ZipInputStream zip = new ZipInputStream(
            new ByteArrayInputStream(download.zipBytes()))) {
            assertEquals("config/main.txt", zip.getNextEntry().getName());
            assertEquals("literal config\n",
                new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            assertNull(zip.getNextEntry());
        }
        assertEquals(preview.saved().fingerprint(), storedHistory.get().fingerprint());
        assertEquals(created.basis(), storedHistory.get().basis());

        AtomicReference<ConfigurationHistoryReadModel.UserReport> storedReport =
            new AtomicReference<>();
        var reportService = new ConfigurationUserReportService(
            (projectId, generationId) -> new ConfigurationUserReportService.TrustedGeneration(
                projectId, generationId, storedHistory.get().createdAt()),
            () -> "report-1", CLOCK,
            (generation, accepted, reportId) -> {
                var saved = new ConfigurationHistoryReadModel.UserReport(reportId,
                    generation.generationId(), accepted.reportedState(), accepted.reasonCode(),
                    accepted.source(), accepted.reportedAt());
                storedReport.set(saved);
                return saved;
            });
        reportService.report("project-1", "generation-1",
            new ConfigurationUserReport.Submission(
                ConfigurationUserReport.ReportedState.APPLIED, null));
        var historyService = new ConfigurationHistoryQueryService(
            projectId -> created.basis(),
            (projectId, generationId) -> storedHistory.get(),
            (projectId, generationId) -> storedReport.get());
        var history = historyService.find("project-1", "generation-1");
        assertEquals(ConfigurationGenerationHistory.Validity.CURRENT, history.validity());
        assertEquals(ConfigurationUserReport.ReportedState.APPLIED,
            history.latestUserReport().reportedState());
        assertEquals(ConfigurationGenerationHistory.VerificationStatus.NOT_RUN,
            history.generation().verificationStatus());
    }
}
