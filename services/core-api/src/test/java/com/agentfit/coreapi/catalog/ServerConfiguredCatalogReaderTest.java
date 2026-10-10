package com.agentfit.coreapi.catalog;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfiguredCatalogReaderTest {
    @TempDir Path directory;

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
            .withUserConfiguration(CatalogSourceConfiguration.class);

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
