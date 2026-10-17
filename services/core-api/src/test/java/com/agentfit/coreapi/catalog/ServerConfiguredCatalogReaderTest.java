package com.agentfit.coreapi.catalog;

import com.agentfit.autoconfigure.CatalogSourceConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfiguredCatalogReaderTest {
    @TempDir Path directory;

    @Test
    void demoCatalogIsReadableOnlyWithItsExplicitHash() {
        Path demo = Path.of("..", "..", "demo", "catalog-synthetic").toAbsolutePath().normalize();
        String hash = "453860c7e6d0be7871813e185ca52259bba774426318ced7451efe2b53aee2d6";
        assertEquals(hash, new ServerConfiguredCatalogReader(demo, hash).current().approvedHash());
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> new ServerConfiguredCatalogReader(demo, "a".repeat(64)).current());
    }

    @Test
    void returnsOnlyTheServerApprovedIntactRelease() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var reader = new ServerConfiguredCatalogReader(directory, hash);

        var approved = reader.current();
        assertEquals(directory, approved.directory());
        assertEquals(hash, approved.approvedHash());

        Files.writeString(directory.resolve("tools.json"), "{}");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, reader::current);
    }

    @Test
    void rejectsAHashThatDoesNotMatchTheRelease() throws Exception {
        SyntheticCatalogBundle.write(directory);
        var reader = new ServerConfiguredCatalogReader(directory, "a".repeat(64));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, reader::current);
    }

    @Test
    void springExposesNoCatalogWithoutBothServerSettings() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CatalogSourceConfiguration.class));

        runner.run(context -> assertFalse(context.containsBean("approvedCatalogReader")));
        runner.withPropertyValues("agentfit.catalog.directory=" + directory)
            .run(context -> assertFalse(context.containsBean("approvedCatalogReader")));
        runner.withPropertyValues("agentfit.catalog.directory=" + directory,
                "agentfit.catalog.approved-hash=" + hash)
            .run(context -> {
                var reader = context.getBean(RecommendationCreationService.ApprovedCatalogReader.class);
                assertEquals(hash, reader.current().approvedHash());
            });
    }
}
