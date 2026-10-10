package com.agentfit.coreapi.configuration.history;

import com.agentfit.autoconfigure.HistoryWorkflowConfiguration;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReportService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import static org.junit.jupiter.api.Assertions.*;

class HistoryWorkflowConfigurationTest {
    @Test
    void historyQueryNeedsAllThreeOwnerCheckedReaders() {
        var runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HistoryWorkflowConfiguration.class))
            .withBean(ConfigurationHistoryQueryService.OwnerCheckedBasisReader.class,
                () -> projectId -> null)
            .withBean(ConfigurationHistoryQueryService.GenerationReader.class,
                () -> (projectId, generationId) -> null);

        runner.run(context -> assertFalse(context.containsBean("configurationHistoryQueryService")));
        runner.withBean(ConfigurationHistoryQueryService.LatestReportReader.class,
                () -> (projectId, generationId) -> null)
            .run(context -> assertNotNull(context.getBean(ConfigurationHistoryQueryService.class)));
    }

    @Test
    void userReportNeedsTrustedGenerationStoreServerIdAndClock() {
        var runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HistoryWorkflowConfiguration.class))
            .withBean(ConfigurationUserReportService.TrustedGenerationReader.class,
                () -> (projectId, generationId) -> null)
            .withBean(ConfigurationUserReportService.ReportStore.class,
                () -> (generation, accepted, reportId) -> null)
            .withBean(Clock.class,
                () -> Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));

        runner.run(context -> assertFalse(context.containsBean("configurationUserReportService")));
        runner.withBean(HistoryWorkflowConfiguration.ReportIdSource.class,
                () -> () -> "report-1")
            .run(context -> assertNotNull(context.getBean(ConfigurationUserReportService.class)));
    }
}
