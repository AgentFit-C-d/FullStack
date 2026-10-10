package com.agentfit.coreapi.configuration.history;

import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationHistoryQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private static final PreviewBasis BASIS = new PreviewBasis("project-1", 1,
        "profile-1", 1, "event-1", 0, 0, 1, "catalog-1", "a".repeat(64));

    @Test
    void readsOwnerCheckedGenerationAndLatestUserReportWithoutClaimingVerification() {
        var report = new ConfigurationHistoryReadModel.UserReport("report-1", "generation-1",
            ConfigurationUserReport.ReportedState.APPLIED, null,
            ConfigurationUserReport.Source.USER, NOW.plusSeconds(30));
        var service = new ConfigurationHistoryQueryService(
            projectId -> BASIS,
            (projectId, generationId) -> history(),
            (projectId, generationId) -> report);

        var result = service.find("project-1", "generation-1");

        assertEquals(ConfigurationGenerationHistory.Validity.CURRENT, result.validity());
        assertEquals(ConfigurationGenerationHistory.VerificationStatus.NOT_RUN,
            result.generation().verificationStatus());
        assertEquals(ConfigurationUserReport.Source.USER, result.latestUserReport().source());
    }

    @Test
    void missingGenerationReturnsNullOnlyAfterOwnerCheck() {
        AtomicInteger basisReads = new AtomicInteger();
        AtomicInteger reportReads = new AtomicInteger();
        var service = new ConfigurationHistoryQueryService(
            projectId -> { basisReads.incrementAndGet(); return BASIS; },
            (projectId, generationId) -> null,
            (projectId, generationId) -> { reportReads.incrementAndGet(); return null; });

        assertNull(service.find("project-1", "missing"));
        assertEquals(1, basisReads.get());
        assertEquals(0, reportReads.get());
    }

    @Test
    void changedEnvironmentMarksGenerationStale() {
        var changed = new PreviewBasis("project-1", 1, "profile-1", 1, "event-1",
            0, 0, 2, "catalog-1", "a".repeat(64));
        var service = new ConfigurationHistoryQueryService(projectId -> changed,
            (projectId, generationId) -> history(),
            (projectId, generationId) -> null);

        assertEquals(ConfigurationGenerationHistory.Validity.STALE,
            service.find("project-1", "generation-1").validity());
    }

    @Test
    void refusesForeignCurrentBasisBeforeReadingGeneration() {
        AtomicInteger generationReads = new AtomicInteger();
        var foreign = new PreviewBasis("other-project", 1, "profile-1", 1,
            "event-1", 0, 0, 1, "catalog-1", "a".repeat(64));
        var service = new ConfigurationHistoryQueryService(projectId -> foreign,
            (projectId, generationId) -> { generationReads.incrementAndGet(); return history(); },
            (projectId, generationId) -> null);

        assertThrows(IllegalArgumentException.class,
            () -> service.find("project-1", "generation-1"));
        assertEquals(0, generationReads.get());
    }

    private static ConfigurationGenerationHistory history() {
        var input = new PreviewFingerprintInput(BASIS, "rec-1", List.of("tool-1"),
            List.of(), "static-v1",
            List.of(new PreviewInputFile("main", "config/main.txt", "safe config\n")),
            ExistingState.UNKNOWN, List.of());
        return ConfigurationGenerationHistory.capture("generation-1", "preview-1", "approval-1",
            PreviewFingerprint.compute(input).fingerprint(), input,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
