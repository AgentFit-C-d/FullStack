package com.agentfit.coreapi.recommendation;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Set;

/** Caller must pass A-confirmed/AI-validated capability IDs and A15's declared install versions. */
public record RecommendationInput(
    Set<String> requiredCapabilityKeys,
    boolean hasRelevantUndeterminedCapability,
    boolean hasRelevantPendingConflict,
    EnvironmentTarget environment,
    Map<String, String> installedToolVersions
) {
    public RecommendationInput {
        requiredCapabilityKeys = Set.copyOf(requiredCapabilityKeys);
        // Null values mean a declared component whose version is unknown.
        if (installedToolVersions != null && installedToolVersions.entrySet().stream()
            .anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() != null && entry.getValue().isBlank())) {
            throw new IllegalArgumentException("invalid installed component declaration");
        }
        installedToolVersions = installedToolVersions == null ? null
            : Collections.unmodifiableMap(new HashMap<>(installedToolVersions));
    }
}
