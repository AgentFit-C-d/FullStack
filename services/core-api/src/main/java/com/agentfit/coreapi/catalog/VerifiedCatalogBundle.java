package com.agentfit.coreapi.catalog;

import java.util.Map;

/** Text from files whose bytes matched the manifest. Semantic support validation follows separately. */
public record VerifiedCatalogBundle(String releaseId, String catalogHash, Map<String, String> files) {
    public VerifiedCatalogBundle {
        files = Map.copyOf(files);
    }
}
