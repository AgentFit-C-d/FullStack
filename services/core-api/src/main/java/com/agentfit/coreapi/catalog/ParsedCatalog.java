package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.recommendation.selection.PermissionMapping;
import java.util.List;
import java.util.Map;

/** A semantic projection of a hash-verified release, including its review sources. */
public record ParsedCatalog(String catalogHash, CatalogRelease release,
                            List<PermissionMapping> permissionMappings,
                            Map<String, VerificationEvidence> evidence) {
    public ParsedCatalog {
        permissionMappings = List.copyOf(permissionMappings);
        evidence = Map.copyOf(evidence);
    }
}
