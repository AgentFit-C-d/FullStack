package com.agentfit.coreapi.configuration.report;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ConfigurationUserReportTest {
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final Clock SERVER_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void appliedReportHasNoFailureReasonAndServerAssignedSourceAndTime() {
        var accepted = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null),
            SERVER_CLOCK);

        assertEquals(ConfigurationUserReport.ReportedState.APPLIED, accepted.reportedState());
        assertNull(accepted.reasonCode());
        assertEquals(ConfigurationUserReport.Source.USER, accepted.source());
        assertEquals(NOW, accepted.reportedAt());
    }

    @Test
    void failedReportRequiresOneOfTheDefinedReasons() {
        for (ConfigurationUserReport.ReasonCode reason : ConfigurationUserReport.ReasonCode.values()) {
            var accepted = ConfigurationUserReport.accept(
                new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.FAILED, reason),
                SERVER_CLOCK);
            assertEquals(reason, accepted.reasonCode());
            assertEquals(ConfigurationUserReport.Source.USER, accepted.source());
        }
    }

    @Test
    void rejectsMissingOrContradictoryReports() {
        assertThrows(IllegalArgumentException.class, () -> ConfigurationUserReport.accept(null, SERVER_CLOCK));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(null, null), SERVER_CLOCK));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.FAILED, null),
            SERVER_CLOCK));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED,
                ConfigurationUserReport.ReasonCode.UNKNOWN), SERVER_CLOCK));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null), null));
    }
}
