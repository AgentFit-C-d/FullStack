package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final String HASH = "30c2557218e9d92c7b2d254c8df3aaf5d8960ebb13aece7f7146d1f65b567c7f";

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

    @Test
    void syntheticDownloadIncludesAReviewableDoNotInstallGuide() {
        Path catalog = Path.of("..", "..", "demo", "catalog-synthetic").toAbsolutePath().normalize();
        var files = CatalogStaticTemplateRenderer.render(catalog, HASH,
            Set.of("demo-all-capabilities-skill"));

        assertEquals(Set.of("config/demo.txt", "README.md"), files.stream()
            .map(file -> file.relativePath()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(files.stream().filter(file -> file.relativePath().equals("README.md"))
            .findFirst().orElseThrow().content().contains("Do not install"));
    }
}
