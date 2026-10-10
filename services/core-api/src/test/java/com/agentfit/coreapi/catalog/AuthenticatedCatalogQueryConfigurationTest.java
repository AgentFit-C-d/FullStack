package com.agentfit.coreapi.catalog;

import com.agentfit.autoconfigure.AuthenticatedCatalogQueryConfiguration;
import com.agentfit.autoconfigure.CatalogSourceConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedCatalogQueryConfigurationTest {
    @TempDir Path directory;

    @Test
    void queryIsUnavailableUntilAuthenticationAndApprovedCatalogAreConfigured() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CatalogSourceConfiguration.class,
                AuthenticatedCatalogQueryConfiguration.class));

        runner.run(context -> assertFalse(context.containsBean("authenticatedCatalogQueryService")));
        runner.withBean(AuthenticatedCatalogQueryService.AuthenticatedCaller.class,
                () -> () -> "user-1")
            .run(context -> assertFalse(context.containsBean("authenticatedCatalogQueryService")));
        runner.withPropertyValues("agentfit.catalog.directory=" + directory,
                "agentfit.catalog.approved-hash=" + hash)
            .run(context -> assertFalse(context.containsBean("authenticatedCatalogQueryService")));
    }

    @Test
    void authenticatedQueryUsesServerApprovedCatalogWhenBothPortsExist() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CatalogSourceConfiguration.class,
                AuthenticatedCatalogQueryConfiguration.class))
            .withPropertyValues("agentfit.catalog.directory=" + directory,
                "agentfit.catalog.approved-hash=" + hash)
            .withBean(AuthenticatedCatalogQueryService.AuthenticatedCaller.class,
                () -> () -> "user-1")
            .run(context -> {
                var service = context.getBean(AuthenticatedCatalogQueryService.class);
                var result = service.list(new CatalogReadService.Filter("WINDOWS", "example-client",
                    "1.0", "cap_document_reference"));
                assertEquals(hash, result.catalogHash());
                assertEquals(1, result.items().size());
            });
    }
}
