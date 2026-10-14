package com.agentfit.coreapi.recommendation.selection;

import java.util.Set;

public record PermissionMapping(String toolKey, String mappingKey,
                                boolean required, Set<PermissionPolicy> supportedPolicies) {
    public PermissionMapping {
        supportedPolicies = Set.copyOf(supportedPolicies);
    }
}
