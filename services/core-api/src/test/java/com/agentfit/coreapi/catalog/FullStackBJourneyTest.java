package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
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
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FullStackBJourneyTest {
    @TempDir Path directory;

    @Test
    void connectsValidatedAiRecommendationToCurrentPreviewApprovalExportAndUnverifiedReport() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        EnvironmentTarget target = new EnvironmentTarget("WINDOWS", "example-client", "1.0");
        PreviewBasis basis = basis(hash, 1);
        Clock clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
        var claims = CapabilityKey.keys().stream().sorted().map(key -> new AiCapabilityIntake.Claim(key,
            key.equals("cap_document_reference") ? AiCapabilityIntake.Need.REQUIRED
                : AiCapabilityIntake.Need.OPTIONAL,
            "Confirmed profile", List.of("profile.features"))).toList();
        var recommendation = CatalogRecommendationWorkflow.evaluate(directory, hash,
            new CatalogRecommendationWorkflow.Request(claims, List.of(),
                Set.of("profile.features"), false, target, Map.of()));
        assertEquals(RecommendationDecision.Status.RECOMMENDED, recommendation.decision().status());
        StoredRecommendationState storedRecommendation = new StoredRecommendationState("rec-1", basis,
            recommendation.decision().status(), recommendation.decision().toolKeys());
        CatalogPreviewRequest request = new CatalogPreviewRequest(basis, "rec-1", target,
            recommendation.decision().toolKeys(),
            List.of(new PermissionSelection("example-tool", "read", PermissionPolicy.ASK_EACH_TIME)),
            ExistingState.UNKNOWN, List.of(), "static-v1");
        PreviewFingerprintResult result = CatalogPreviewAssembler.assemble(directory, hash,
            storedRecommendation, basis, target, request);
        StoredPreviewState preview = new StoredPreviewState("preview-1", basis,
            result.fingerprint(), Instant.now(clock).minusSeconds(60),
            Instant.now(clock).plusSeconds(600));

        PreviewBasis changedEnvironment = basis(hash, 2);
        assertThrows(IllegalStateException.class,
            () -> CatalogReadyPreviewApprovalWorkflow.issue(directory, hash, preview,
                changedEnvironment, "preview-1", result.fingerprint(), true, "approval-1", clock));

        StoredApprovalState approval = CatalogReadyPreviewApprovalWorkflow.issue(directory, hash,
            preview, basis, "preview-1", result.fingerprint(), true, "approval-1", clock);
        assertThrows(IllegalStateException.class,
            () -> CatalogApprovedConfigurationWorkflow.generate(directory, hash,
                storedRecommendation, changedEnvironment, target, request, preview, approval,
                "approval-1", "preview-1", result.fingerprint(), "obsolete-generation", clock));
        var generated = CatalogApprovedConfigurationWorkflow.generate(directory, hash,
            storedRecommendation, basis, target, request, preview, approval,
            "approval-1", "preview-1", result.fingerprint(), "generation-1", clock);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(generated.zipBytes()),
            StandardCharsets.UTF_8)) {
            assertEquals("config/main.txt", zip.getNextEntry().getName());
            assertArrayEquals("literal config\n".getBytes(StandardCharsets.UTF_8), zip.readAllBytes());
            assertNull(zip.getNextEntry());
        }

        ConfigurationUserReport.Accepted accepted = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null),
            Clock.offset(clock, java.time.Duration.ofSeconds(30)));
        var report = new ConfigurationHistoryReadModel.UserReport("report-1", generated.history().id(),
            accepted.reportedState(), accepted.reasonCode(), accepted.source(), accepted.reportedAt());
        var view = ConfigurationHistoryReadModel.project(generated.history(), changedEnvironment, report);
        assertEquals(ConfigurationGenerationHistory.Validity.STALE, view.validity());
        assertEquals(ConfigurationGenerationHistory.VerificationStatus.NOT_RUN,
            view.generation().verificationStatus());
        assertEquals(ConfigurationUserReport.ReportedState.APPLIED,
            view.latestUserReport().reportedState());
    }

    private static PreviewBasis basis(String hash, long environmentVersion) {
        return new PreviewBasis("project-1", 1, "profile-1", 1, "event-1", 0, 0,
            environmentVersion, "synthetic-recommendation", hash);
    }
}
