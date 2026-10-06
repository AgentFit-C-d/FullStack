package com.agentfit.coreapi.catalog.model;

import java.util.List;
import java.util.Set;

/** One deployable unit. Included component keys are not independently counted as tools. */
public record CatalogTool(
    String key,
    String version,
    Set<String> capabilityKeys,
    Set<String> dependencyKeys,
    Set<String> conflictKeys,
    Set<String> includedComponentKeys,
    List<ToolSupport> support
) {
    public CatalogTool {
        capabilityKeys = Set.copyOf(capabilityKeys);
        dependencyKeys = Set.copyOf(dependencyKeys);
        conflictKeys = Set.copyOf(conflictKeys);
        includedComponentKeys = Set.copyOf(includedComponentKeys);
        support = List.copyOf(support);
    }
}
