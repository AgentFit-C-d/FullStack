package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.domain.analysis.client.AiServiceFailureException;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiCapabilityFailureMappingTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void providerAndContractFailuresStayFailuresAndNeverBecomeNormalEmptyRecommendation() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        AtomicReference<String> saved = new AtomicReference<>();
        var service = new RecommendationCreationService(
            AiCapabilityFailureMappingTest::snapshot,
            input -> { throw new AiServiceFailureException(AiServiceFailureException.Kind.PROVIDER,
                "AI_HTTP_500", "provider unavailable"); },
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            (current, basis, result) -> { saved.set("saved"); return "unexpected"; });
        var failure = assertThrows(AiServiceFailureException.class,
            () -> service.create("project-1", new RecommendationCreationService.ExpectedVersions(7, 1, 1)));
        assertEquals(AiServiceFailureException.Kind.PROVIDER, failure.kind());
        assertNull(saved.get());
    }

    @Test
    void validUndeterminedClaimsRemainNeedsInformation() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        AtomicReference<com.agentfit.coreapi.recommendation.RecommendationDecision.Status> status = new AtomicReference<>();
        var service = new RecommendationCreationService(
            AiCapabilityFailureMappingTest::snapshot,
            input -> new RecommendationCreationService.CapabilityAssessment(
                CapabilityKey.keys().stream().sorted().map(key -> new AiCapabilityIntake.Claim(key,
                    AiCapabilityIntake.Need.UNDETERMINED, "more information required",
                    List.of("profile.item.features.0"))).toList(),
                List.of(new AiCapabilityIntake.Question("profile.item.features.0", "confirm this field"))),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            (current, basis, result) -> { status.set(result.decision().status()); return "needs-information"; });
        service.create("project-1", new RecommendationCreationService.ExpectedVersions(7, 1, 1));
        assertEquals(com.agentfit.coreapi.recommendation.RecommendationDecision.Status.NEEDS_INFORMATION, status.get());
    }

    private static RecommendationCreationService.OwnerCheckedSnapshot snapshot(String projectId) {
        return new RecommendationCreationService.OwnerCheckedSnapshot(projectId, 7, "confirmed-1", 1,
            "confirmation-1", 1, 1, 1, Set.of("profile.item.features.0"),
            Map.of("profile.item.features.0", "document search"), false,
            new EnvironmentTarget("WINDOWS", "client", "1.0"), Map.of());
    }
}
