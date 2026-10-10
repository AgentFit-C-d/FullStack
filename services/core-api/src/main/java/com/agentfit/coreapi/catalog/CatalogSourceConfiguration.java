package com.agentfit.coreapi.catalog;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No Catalog bean exists until deployment supplies a separate approved hash and directory. */
@Configuration(proxyBeanMethods = false)
public class CatalogSourceConfiguration {
    @Bean("approvedCatalogReader")
    @ConditionalOnProperty(prefix = "agentfit.catalog", name = {"directory", "approved-hash"})
    RecommendationCreationService.ApprovedCatalogReader approvedCatalogReader(
        @Value("${agentfit.catalog.directory}") String directory,
        @Value("${agentfit.catalog.approved-hash}") String hash) {
        return new ServerConfiguredCatalogReader(Path.of(directory), hash);
    }
}
