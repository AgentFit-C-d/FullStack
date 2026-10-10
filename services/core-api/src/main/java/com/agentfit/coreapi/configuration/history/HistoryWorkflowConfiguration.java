package com.agentfit.coreapi.configuration.history;

import com.agentfit.coreapi.configuration.report.ConfigurationUserReportService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** History reads and user reports require A's owner-checked persistence ports. */
@Configuration(proxyBeanMethods = false)
public class HistoryWorkflowConfiguration {
    @FunctionalInterface
    public interface ReportIdSource {
        String newReportId();
    }

    @Bean
    @ConditionalOnBean({ConfigurationHistoryQueryService.OwnerCheckedBasisReader.class,
        ConfigurationHistoryQueryService.GenerationReader.class,
        ConfigurationHistoryQueryService.LatestReportReader.class})
    ConfigurationHistoryQueryService configurationHistoryQueryService(
        ConfigurationHistoryQueryService.OwnerCheckedBasisReader bases,
        ConfigurationHistoryQueryService.GenerationReader generations,
        ConfigurationHistoryQueryService.LatestReportReader reports) {
        return new ConfigurationHistoryQueryService(bases, generations, reports);
    }

    @Bean
    @ConditionalOnBean({ConfigurationUserReportService.TrustedGenerationReader.class,
        ConfigurationUserReportService.ReportStore.class, ReportIdSource.class, Clock.class})
    ConfigurationUserReportService configurationUserReportService(
        ConfigurationUserReportService.TrustedGenerationReader generations,
        ReportIdSource ids, Clock clock, ConfigurationUserReportService.ReportStore store) {
        return new ConfigurationUserReportService(generations, ids::newReportId, clock, store);
    }
}
