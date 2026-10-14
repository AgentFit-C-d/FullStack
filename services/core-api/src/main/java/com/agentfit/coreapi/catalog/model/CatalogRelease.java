package com.agentfit.coreapi.catalog.model;

import java.util.Map;
import java.util.Set;

/** Immutable in-memory projection of a reviewed release; no DB or remote Catalog access. */
public record CatalogRelease(
    String releaseId,
    Map<String, CatalogTool> tools,
    Set<VerifiedCombination> verifiedCombinations
) {
    public CatalogRelease {
        tools = Map.copyOf(tools);
        verifiedCombinations = Set.copyOf(verifiedCombinations);
    }
}
