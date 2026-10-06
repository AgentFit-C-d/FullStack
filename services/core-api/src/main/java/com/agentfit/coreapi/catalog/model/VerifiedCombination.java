package com.agentfit.coreapi.catalog.model;

import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import java.util.Set;

/** A reviewed complete tool set for one exact OS/Client/version target. */
public record VerifiedCombination(Set<String> toolKeys, EnvironmentTarget target) {
    public VerifiedCombination {
        toolKeys = Set.copyOf(toolKeys);
    }
}
