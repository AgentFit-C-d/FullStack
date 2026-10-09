package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CatalogRecommendationWorkflowTest {
    @TempDir Path directory;

    @Test
    void producesRecommendationFromApprovedCatalogAndCompleteAiAssessment() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        var outcome = CatalogRecommendationWorkflow.evaluate(directory, approvedHash, request());
        assertEquals(RecommendationDecision.Status.RECOMMENDED, outcome.decision().status());
        assertEquals(List.of("example-tool"), outcome.decision().toolKeys());
        assertEquals("synthetic-recommendation", outcome.catalogReleaseId());
        assertEquals(approvedHash, outcome.catalogHash());
        assertEquals(List.of(), outcome.clarificationQuestions());
    }

    @Test
    void rejectsUnapprovedCatalogAndIncompleteAiAssessment() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogRecommendationWorkflow.evaluate(directory, "f".repeat(64), request()));
        var incomplete = new CatalogRecommendationWorkflow.Request(
            request().claims().subList(0, 1), List.of(), Set.of("profile.features"),
            false, new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
        assertThrows(IllegalArgumentException.class,
            () -> CatalogRecommendationWorkflow.evaluate(directory, approvedHash, incomplete));
    }

    @Test
    void keepsAiQuestionsAndReturnsNeedsInformationForUndeterminedCapability() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        var claims = request().claims().stream().map(claim ->
            claim.capabilityKey().equals("cap_document_reference")
                ? new AiCapabilityIntake.Claim(claim.capabilityKey(), AiCapabilityIntake.Need.UNDETERMINED,
                    "Need project context", List.of()) : claim).toList();
        var question = new AiCapabilityIntake.Question("profile.features", "What documents are needed?");
        var input = new CatalogRecommendationWorkflow.Request(claims, List.of(question),
            Set.of("profile.features"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
        var outcome = CatalogRecommendationWorkflow.evaluate(directory, approvedHash, input);
        assertEquals(RecommendationDecision.Status.NEEDS_INFORMATION, outcome.decision().status());
        assertEquals(List.of(question), outcome.clarificationQuestions());
    }

    @Test
    void treatsUnknownInstalledVersionAsQuestionInsteadOfRejectingRequest() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        Map<String, String> installed = new HashMap<>();
        installed.put("example-tool", null);
        var input = new CatalogRecommendationWorkflow.Request(request().claims(), List.of(),
            Set.of("profile.features"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), installed);
        var outcome = CatalogRecommendationWorkflow.evaluate(directory, approvedHash, input);
        assertEquals(RecommendationDecision.Status.NEEDS_INFORMATION, outcome.decision().status());
        assertTrue(outcome.decision().questionCodes().contains("installed_component_version"));
    }

    @Test
    void invalidAiClaimIsRejectedByIntakeWithoutConstructorNullPointer() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        var claims = new ArrayList<>(request().claims());
        claims.set(0, null);
        var input = new CatalogRecommendationWorkflow.Request(claims, List.of(),
            Set.of("profile.features"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
        assertThrows(IllegalArgumentException.class,
            () -> CatalogRecommendationWorkflow.evaluate(directory, approvedHash, input));
    }

    private CatalogRecommendationWorkflow.Request request() {
        var claims = CapabilityKey.keys().stream().sorted().map(key ->
            new AiCapabilityIntake.Claim(key,
                key.equals("cap_document_reference") ? AiCapabilityIntake.Need.REQUIRED
                    : AiCapabilityIntake.Need.OPTIONAL,
                "Based on confirmed project profile", List.of("profile.features"))).toList();
        return new CatalogRecommendationWorkflow.Request(claims, List.of(),
            Set.of("profile.features"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
    }

}
