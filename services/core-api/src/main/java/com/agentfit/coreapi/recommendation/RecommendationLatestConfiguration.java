package com.agentfit.coreapi.recommendation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Latest reads never activate without A's owner-checked basis and stored recommendation. */
@Configuration(proxyBeanMethods = false)
public class RecommendationLatestConfiguration {
    @Bean
    @ConditionalOnBean({RecommendationLatestService.OwnerCheckedBasisReader.class,
        RecommendationLatestService.StoredRecommendationReader.class})
    RecommendationLatestService recommendationLatestService(
        RecommendationLatestService.OwnerCheckedBasisReader bases,
        RecommendationLatestService.StoredRecommendationReader recommendations) {
        return new RecommendationLatestService(bases, recommendations);
    }
}
