package com.agentfit.autoconfigure;

import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/** B workflows activate only after their trusted A/AI ports and approved Catalog exist. */
@AutoConfiguration(after = CatalogSourceConfiguration.class)
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
