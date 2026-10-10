package com.agentfit.coreapi.configuration.report;

import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationUserReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void storesUserStatementOnlyAfterOwnerCheckedGenerationLookup() {
        AtomicInteger saves = new AtomicInteger();
        var service = new ConfigurationUserReportService(
            (projectId, generationId) -> generation(projectId, generationId),
            () -> "report-1", CLOCK,
            (trusted, accepted, reportId) -> {
                assertEquals("project-1", trusted.projectId());
                assertEquals(ConfigurationUserReport.Source.USER, accepted.source());
                saves.incrementAndGet();
                return new ConfigurationHistoryReadModel.UserReport(reportId, trusted.generationId(),
                    accepted.reportedState(), accepted.reasonCode(), accepted.source(),
                    accepted.reportedAt());
            });

        var stored = service.report("project-1", "generation-1",
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null));

        assertEquals("report-1", stored.id());
        assertEquals(ConfigurationUserReport.Source.USER, stored.source());
        assertEquals(1, saves.get());
    }

    @Test
    void invalidSubmissionCannotBeSaved() {
        var service = new ConfigurationUserReportService(
            ConfigurationUserReportServiceTest::generation,
            () -> "report-1", CLOCK,
            (trusted, accepted, reportId) -> fail("invalid report must not be saved"));

        assertThrows(IllegalArgumentException.class, () -> service.report("project-1", "generation-1",
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.FAILED, null)));
    }

    @Test
    void foreignGenerationIsRejectedBeforeSave() {
        var service = new ConfigurationUserReportService(
            (projectId, generationId) -> generation("other-project", generationId),
            () -> "report-1", CLOCK,
            (trusted, accepted, reportId) -> fail("foreign report must not be saved"));

        assertThrows(IllegalArgumentException.class, () -> service.report("project-1", "generation-1",
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null)));
    }

    @Test
    void invalidServerReportIdCannotReachPersistence() {
        for (String id : new String[] {null, " ", "x".repeat(129)}) {
            var service = new ConfigurationUserReportService(
                ConfigurationUserReportServiceTest::generation,
                () -> id, CLOCK,
                (trusted, accepted, reportId) -> fail("invalid ID must not be saved"));

            assertThrows(IllegalArgumentException.class, () -> service.report("project-1",
                "generation-1", new ConfigurationUserReport.Submission(
                    ConfigurationUserReport.ReportedState.APPLIED, null)));
        }
    }

    private static ConfigurationUserReportService.TrustedGeneration generation(String projectId,
                                                                                String generationId) {
        return new ConfigurationUserReportService.TrustedGeneration(projectId, generationId,
            NOW.minusSeconds(300));
    }
}
