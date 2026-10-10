package com.agentfit.coreapi.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Activate Catalog queries only after A supplies authentication and an approved release exists. */
@Configuration(proxyBeanMethods = false)
public class AuthenticatedCatalogQueryConfiguration {
    @Bean
    @ConditionalOnBean({AuthenticatedCatalogQueryService.AuthenticatedCaller.class,
        RecommendationCreationService.ApprovedCatalogReader.class})
    AuthenticatedCatalogQueryService authenticatedCatalogQueryService(
        AuthenticatedCatalogQueryService.AuthenticatedCaller caller,
        RecommendationCreationService.ApprovedCatalogReader catalogs) {
        return new AuthenticatedCatalogQueryService(caller, catalogs);
    }
}
