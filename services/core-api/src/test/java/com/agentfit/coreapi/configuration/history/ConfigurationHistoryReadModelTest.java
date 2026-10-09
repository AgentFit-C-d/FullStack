package com.agentfit.coreapi.configuration.history;

import static org.junit.jupiter.api.Assertions.*;

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
import org.junit.jupiter.api.Test;

class ConfigurationHistoryReadModelTest {
    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PreviewBasis BASIS = new PreviewBasis("project-1", 1,
        "profile-1", 1, "event-1", 0, 0, 1, "catalog-1", "a".repeat(64));

    @Test
    void showsLatestUserStatementSeparatelyFromUnverifiedGeneration() {
        ConfigurationGenerationHistory history = history();
        var report = new ConfigurationHistoryReadModel.UserReport("report-1", "generation-1",
            ConfigurationUserReport.ReportedState.APPLIED, null,
            ConfigurationUserReport.Source.USER, NOW.plusSeconds(30));

        var view = ConfigurationHistoryReadModel.project(history, BASIS, report);

        assertEquals(ConfigurationGenerationHistory.Validity.CURRENT, view.validity());
        assertEquals(ConfigurationGenerationHistory.Status.GENERATED, view.generation().status());
        assertEquals(ConfigurationGenerationHistory.VerificationStatus.NOT_RUN,
            view.generation().verificationStatus());
        assertEquals(ConfigurationGenerationHistory.VerificationSource.NONE,
            view.generation().verificationSource());
        assertEquals(ConfigurationUserReport.Source.USER, view.latestUserReport().source());
        assertEquals(ConfigurationUserReport.ReportedState.APPLIED,
            view.latestUserReport().reportedState());
    }

    @Test
    void marksOldBasisStaleWithoutClaimingApplicationOrVerification() {
        ConfigurationGenerationHistory history = history();
        PreviewBasis changed = new PreviewBasis("project-1", 1,
            "profile-1", 1, "event-1", 0, 0, 2, "catalog-1", "a".repeat(64));

        var view = ConfigurationHistoryReadModel.project(history, changed, null);

        assertEquals(ConfigurationGenerationHistory.Validity.STALE, view.validity());
        assertNull(view.latestUserReport());
        assertEquals(ConfigurationGenerationHistory.VerificationStatus.NOT_RUN,
            view.generation().verificationStatus());
    }

    @Test
    void rejectsReportForAnotherGenerationOrImpossibleReportMetadata() {
        ConfigurationGenerationHistory history = history();
        var wrongGeneration = new ConfigurationHistoryReadModel.UserReport("report-1", "other",
            ConfigurationUserReport.ReportedState.FAILED,
            ConfigurationUserReport.ReasonCode.COPY_FAILED,
            ConfigurationUserReport.Source.USER, NOW.plusSeconds(30));
        assertThrows(IllegalArgumentException.class,
            () -> ConfigurationHistoryReadModel.project(history, BASIS, wrongGeneration));
        assertThrows(IllegalArgumentException.class,
            () -> new ConfigurationHistoryReadModel.UserReport("report-1", "generation-1",
                ConfigurationUserReport.ReportedState.APPLIED,
                ConfigurationUserReport.ReasonCode.COPY_FAILED,
                ConfigurationUserReport.Source.USER, NOW.plusSeconds(30)));
        assertThrows(IllegalArgumentException.class,
            () -> new ConfigurationHistoryReadModel.UserReport("report-1", "generation-1",
                ConfigurationUserReport.ReportedState.FAILED, null,
                ConfigurationUserReport.Source.USER, NOW.plusSeconds(30)));
        assertThrows(IllegalArgumentException.class,
            () -> ConfigurationHistoryReadModel.project(history, null, null));
        var beforeGeneration = new ConfigurationHistoryReadModel.UserReport("report-1", "generation-1",
            ConfigurationUserReport.ReportedState.APPLIED, null,
            ConfigurationUserReport.Source.USER, NOW.minusSeconds(1));
        assertThrows(IllegalArgumentException.class,
            () -> ConfigurationHistoryReadModel.project(history, BASIS, beforeGeneration));
        PreviewBasis otherProject = new PreviewBasis("other-project", 1,
            "profile-1", 1, "event-1", 0, 0, 1, "catalog-1", "a".repeat(64));
        assertThrows(IllegalArgumentException.class,
            () -> ConfigurationHistoryReadModel.project(history, otherProject, null));
    }

    private static ConfigurationGenerationHistory history() {
        PreviewFingerprintInput input = new PreviewFingerprintInput(BASIS, "rec-1",
            List.of("tool-1"), List.of(), "static-v1",
            List.of(new PreviewInputFile("main", "config/main.txt", "safe config\n")),
            ExistingState.UNKNOWN, List.of());
        return ConfigurationGenerationHistory.capture("generation-1", "preview-1", "approval-1",
            PreviewFingerprint.compute(input).fingerprint(), input, CLOCK);
    }
}
