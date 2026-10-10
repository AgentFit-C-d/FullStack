package com.agentfit.autoconfigure;

import com.agentfit.coreapi.catalog.AuthenticatedCatalogQueryService;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/** Activate Catalog queries only after A supplies authentication and an approved release exists. */
@AutoConfiguration(after = CatalogSourceConfiguration.class)
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
