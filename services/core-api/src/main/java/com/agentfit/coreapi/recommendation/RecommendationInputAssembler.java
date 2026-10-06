package com.agentfit.coreapi.recommendation;

import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.util.Map;

/** Fail-closed bridge from validated AI assessments to B's pure recommendation input. */
public final class RecommendationInputAssembler {
    private RecommendationInputAssembler() {}

    public static RecommendationInput assemble(AiCapabilityIntake.Validated assessment,
                                               boolean hasRelevantPendingConflict,
                                               EnvironmentTarget environment,
                                               Map<String, String> installedToolVersions) {
        // Omitted Capability semantics are not agreed with AI. A partial assessment
        // cannot make an empty required set mean "no additions needed".
        if (assessment == null || assessment.claims().size() != CapabilityKey.values().length) {
            throw new IllegalArgumentException("incomplete AI Capability assessment");
        }
        return new RecommendationInput(assessment.requiredCapabilityKeys(),
            assessment.hasUndeterminedCapability(), hasRelevantPendingConflict,
            environment, installedToolVersions);
    }
}
