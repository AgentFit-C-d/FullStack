package com.agentfit.coreapi.catalog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Objects;

/** Server-owned Catalog source. Candidate directories never become approved by their own hash. */
public final class ServerConfiguredCatalogReader
    implements RecommendationCreationService.ApprovedCatalogReader {
    private final Path directory;
    private final String approvedHash;

    public ServerConfiguredCatalogReader(Path directory, String approvedHash) {
        this.directory = Objects.requireNonNull(directory);
        if (approvedHash == null || !approvedHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid approved Catalog hash");
        }
        this.approvedHash = approvedHash;
    }

    @Override
    public RecommendationCreationService.ApprovedCatalog current() {
        ParsedCatalog catalog = CatalogSemanticParser.load(directory);
        if (!MessageDigest.isEqual(catalog.catalogHash().getBytes(StandardCharsets.US_ASCII),
            approvedHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new CatalogBundleLoader.CatalogUnavailableException("Catalog hash is not approved");
        }
        return new RecommendationCreationService.ApprovedCatalog(directory, approvedHash);
    }
}
