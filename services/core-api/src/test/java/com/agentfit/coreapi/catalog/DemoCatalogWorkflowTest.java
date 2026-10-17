package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DemoCatalogWorkflowTest {
    private static final String HASH = "453860c7e6d0be7871813e185ca52259bba774426318ced7451efe2b53aee2d6";

    @Test
    void syntheticDemoCatalogRecommendsOnlyForMatchingTargetAndCompleteAiClaims() {
        Path catalog = Path.of("..", "..", "demo", "catalog-synthetic").toAbsolutePath().normalize();
        String source = "profile.item.features.0";
        var claims = CapabilityKey.keys().stream().map(key -> new AiCapabilityIntake.Claim(
            key, AiCapabilityIntake.Need.REQUIRED, "confirmed feature", List.of(source))).toList();
        var result = CatalogRecommendationWorkflow.evaluate(catalog, HASH,
            new CatalogRecommendationWorkflow.Request(claims, List.of(), Set.of(source), false,
                new EnvironmentTarget("WINDOWS", "demo-claude-code", "demo-1"), Map.of()));

        assertEquals(RecommendationDecision.Status.RECOMMENDED, result.decision().status());
        assertEquals(List.of("demo-all-capabilities-skill"), result.decision().toolKeys());
        assertEquals("DEMO ONLY - All Capabilities Skill", result.items().getFirst().name());
    }

    @Test
    void incompleteAiClaimsRemainNeedsInformationInDemo() {
        Path catalog = Path.of("..", "..", "demo", "catalog-synthetic").toAbsolutePath().normalize();
        var claims = CapabilityKey.keys().stream().map(key -> new AiCapabilityIntake.Claim(
            key, AiCapabilityIntake.Need.UNDETERMINED, "missing facts", List.of())).toList();
        var result = CatalogRecommendationWorkflow.evaluate(catalog, HASH,
            new CatalogRecommendationWorkflow.Request(claims, List.of(), Set.of(), false,
                new EnvironmentTarget("WINDOWS", "demo-claude-code", "demo-1"), Map.of()));

        assertEquals(RecommendationDecision.Status.NEEDS_INFORMATION, result.decision().status());
    }
}
