package com.agentfit.coreapi.catalog;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A supplies server-issued IDs, clock, owner-checked readers, and atomic persistence ports. */
@Configuration(proxyBeanMethods = false)
public class FinalizationWorkflowConfiguration {
    public interface ServerIssuedIds {
        String approvalId();
        String generationId();
    }

    @Bean
    @ConditionalOnBean({ApprovalCreationService.TrustedPreviewReader.class,
        RecommendationCreationService.ApprovedCatalogReader.class,
        ApprovalCreationService.ApprovalStore.class,
        ServerIssuedIds.class, Clock.class})
    ApprovalCreationService approvalCreationService(
        ApprovalCreationService.TrustedPreviewReader previews,
        RecommendationCreationService.ApprovedCatalogReader catalogs,
        ServerIssuedIds ids, Clock clock, ApprovalCreationService.ApprovalStore store) {
        return new ApprovalCreationService(previews, catalogs, ids::approvalId, clock, store);
    }

    @Bean
    @ConditionalOnBean({ConfigurationDownloadService.TrustedDownloadReader.class,
        RecommendationCreationService.ApprovedCatalogReader.class,
        ConfigurationDownloadService.HistoryStore.class,
        ServerIssuedIds.class, Clock.class})
    ConfigurationDownloadService configurationDownloadService(
        ConfigurationDownloadService.TrustedDownloadReader downloads,
        RecommendationCreationService.ApprovedCatalogReader catalogs,
        ServerIssuedIds ids, Clock clock, ConfigurationDownloadService.HistoryStore historyStore) {
        return new ConfigurationDownloadService(downloads, catalogs,
            ids::generationId, clock, historyStore);
    }
}
