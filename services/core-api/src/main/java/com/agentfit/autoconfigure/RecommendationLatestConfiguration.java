package com.agentfit.autoconfigure;

import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.recommendation.RecommendationLatestService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/** Latest reads never activate without A's owner-checked basis and stored recommendation. */
@AutoConfiguration
public class RecommendationLatestConfiguration {
    @Bean
    @ConditionalOnBean({RecommendationLatestService.OwnerCheckedBasisReader.class,
        RecommendationLatestService.StoredRecommendationReader.class})
    RecommendationLatestService recommendationLatestService(
        RecommendationLatestService.OwnerCheckedBasisReader bases,
        RecommendationLatestService.StoredRecommendationReader recommendations) {
        return new RecommendationLatestService(bases, recommendations);
    }

    @Bean
    @ConditionalOnBean({RecommendationLatestDetailsService.OwnerCheckedCurrentReader.class,
        RecommendationLatestDetailsService.StoredMetadataReader.class,
        RecommendationCreationService.ApprovedCatalogReader.class})
    RecommendationLatestDetailsService recommendationLatestDetailsService(
        RecommendationLatestDetailsService.OwnerCheckedCurrentReader current,
        RecommendationLatestDetailsService.StoredMetadataReader metadata,
        RecommendationCreationService.ApprovedCatalogReader catalogs) {
        return new RecommendationLatestDetailsService(current, metadata, catalogs);
    }
}
