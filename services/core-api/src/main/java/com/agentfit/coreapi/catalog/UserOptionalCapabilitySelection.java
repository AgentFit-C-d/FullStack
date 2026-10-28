package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Records explicit user interest without attributing it to the AI or confirmed document. */
public final class UserOptionalCapabilitySelection {
    private UserOptionalCapabilitySelection() {}

    public static List<AiCapabilityIntake.Claim> apply(List<AiCapabilityIntake.Claim> claims,
                                                        Set<String> selectedKeys) {
        if (claims == null || selectedKeys == null || !CapabilityKey.keys().containsAll(selectedKeys)) {
            throw new IllegalArgumentException("invalid optional Capability selection");
        }
        List<AiCapabilityIntake.Claim> updated = new ArrayList<>();
        Set<String> matched = new java.util.HashSet<>();
        for (AiCapabilityIntake.Claim claim : claims) {
            if (claim == null) throw new IllegalArgumentException("invalid AI claim");
            if (!selectedKeys.contains(claim.capabilityKey())) {
                updated.add(claim);
                continue;
            }
            if (claim.need() != AiCapabilityIntake.Need.NOT_REQUESTED) {
                throw new IllegalArgumentException("only not-requested Capabilities can be added as optional");
            }
            matched.add(claim.capabilityKey());
            updated.add(new AiCapabilityIntake.Claim(claim.capabilityKey(),
                AiCapabilityIntake.Need.OPTIONAL, "사용자가 선택 후보로 추가 요청함",
                List.of("user.optional." + claim.capabilityKey())));
        }
        if (!matched.equals(selectedKeys)) {
            throw new IllegalArgumentException("selected Capability is missing from AI assessment");
        }
        return List.copyOf(updated);
    }
}
