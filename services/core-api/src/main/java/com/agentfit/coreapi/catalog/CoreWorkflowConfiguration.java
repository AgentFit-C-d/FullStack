package com.agentfit.coreapi.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** B workflows activate only after their trusted A/AI ports and approved Catalog exist. */
@Configuration(proxyBeanMethods = false)
public class CoreWorkflowConfiguration {
    @Bean
    @ConditionalOnBean({RecommendationCreationService.OwnerCheckedSnapshotReader.class,
        RecommendationCreationService.CapabilityAnalyzer.class,
        RecommendationCreationService.ApprovedCatalogReader.class,
        RecommendationCreationService.RecommendationStore.class})
    RecommendationCreationService recommendationCreationService(
        RecommendationCreationService.OwnerCheckedSnapshotReader snapshots,
        RecommendationCreationService.CapabilityAnalyzer analyzer,
        RecommendationCreationService.ApprovedCatalogReader catalogs,
        RecommendationCreationService.RecommendationStore store) {
        return new RecommendationCreationService(snapshots, analyzer, catalogs, store);
    }

    @Bean
    @ConditionalOnBean({PreviewCreationService.TrustedContextReader.class,
        RecommendationCreationService.ApprovedCatalogReader.class,
        PreviewCreationService.PreviewStore.class})
    PreviewCreationService previewCreationService(
        PreviewCreationService.TrustedContextReader contexts,
        RecommendationCreationService.ApprovedCatalogReader catalogs,
        PreviewCreationService.PreviewStore store) {
        return new PreviewCreationService(contexts, catalogs, store);
    }
}
