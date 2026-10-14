package com.agentfit.coreapi.catalog;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticatedCatalogQueryServiceTest {
    @TempDir Path directory;

    @Test
    void readsOnlyTheApprovedReleaseAfterAuthentication() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        AtomicInteger authentications = new AtomicInteger();
        var service = new AuthenticatedCatalogQueryService(
            () -> { authentications.incrementAndGet(); return "user-1"; },
            new ServerConfiguredCatalogReader(directory, hash));

        var result = service.list(new CatalogReadService.Filter("WINDOWS", "example-client",
            "1.0", "cap_document_reference"));

        assertEquals(1, authentications.get());
        assertEquals(hash, result.catalogHash());
        assertEquals(1, result.items().size());
    }

    @Test
    void rejectsMissingSessionBeforeReadingCatalog() {
        AtomicInteger catalogReads = new AtomicInteger();
        var service = new AuthenticatedCatalogQueryService(() -> null,
            () -> { catalogReads.incrementAndGet(); return null; });

        assertThrows(AuthenticatedCatalogQueryService.UnauthenticatedException.class,
            () -> service.list(new CatalogReadService.Filter(null, null, null, null)));
        assertEquals(0, catalogReads.get());
    }

    @Test
    void doesNotTurnCatalogFailureIntoAnEmptyList() throws Exception {
        SyntheticCatalogBundle.write(directory);
        var service = new AuthenticatedCatalogQueryService(() -> "user-1",
            new ServerConfiguredCatalogReader(directory, "b".repeat(64)));

        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> service.list(new CatalogReadService.Filter(null, null, null, null)));
    }
}
