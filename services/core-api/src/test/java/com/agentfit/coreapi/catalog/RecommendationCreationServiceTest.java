package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationCreationServiceTest {
    @TempDir Path directory;

    @Test
    void createsRecommendationFromOwnerCheckedSnapshotAiAndApprovedCatalog() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        AtomicInteger saved = new AtomicInteger();
        var service = new RecommendationCreationService(
            projectId -> snapshot(projectId),
            snapshot -> {
                assertEquals("[\"document search\"]", snapshot.aiSourceValues().get("profile.features"));
                return assessment();
            },
            () -> new RecommendationCreationService.ApprovedCatalog(directory, approvedHash),
            (snapshot, basis, result) -> {
                assertEquals("project-1", snapshot.projectId());
                assertEquals("project-1", basis.projectId());
                assertEquals(7, basis.projectVersion());
                assertEquals("synthetic-recommendation", basis.catalogReleaseId());
                assertEquals(approvedHash, basis.catalogHash());
                assertEquals(RecommendationDecision.Status.RECOMMENDED, result.decision().status());
                saved.incrementAndGet();
                return "recommendation-1";
            });

        var created = service.create("project-1");

        assertEquals("recommendation-1", created.recommendationId());
        assertEquals(List.of("example-tool"), created.result().decision().toolKeys());
        assertEquals(1, saved.get());
    }

    @Test
    void refusesForeignSnapshotBeforeCallingAiOrPersistence() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        AtomicInteger aiCalls = new AtomicInteger();
        var service = new RecommendationCreationService(
            projectId -> snapshot("other-project"),
            snapshot -> { aiCalls.incrementAndGet(); return assessment(); },
            () -> new RecommendationCreationService.ApprovedCatalog(directory, approvedHash),
            (snapshot, basis, result) -> fail("foreign project must not be saved"));

        assertThrows(IllegalArgumentException.class, () -> service.create("project-1"));
        assertEquals(0, aiCalls.get());
    }

    @Test
    void doesNotPersistWhenAiFails() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        var service = new RecommendationCreationService(
            RecommendationCreationServiceTest::snapshot,
            snapshot -> { throw new IllegalStateException("AI unavailable"); },
            () -> new RecommendationCreationService.ApprovedCatalog(directory, approvedHash),
            (snapshot, basis, result) -> fail("failed assessment must not be saved"));

        assertThrows(IllegalStateException.class, () -> service.create("project-1"));
    }

    @Test
    void propagatesAtomicSaveConflictInsteadOfReturningUnstoredRecommendation() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        var service = new RecommendationCreationService(
            RecommendationCreationServiceTest::snapshot,
            snapshot -> assessment(),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, approvedHash),
            (snapshot, basis, result) -> {
                throw new RecommendationCreationService.SnapshotChangedException();
            });

        assertThrows(RecommendationCreationService.SnapshotChangedException.class,
            () -> service.create("project-1"));
    }

    private static RecommendationCreationService.OwnerCheckedSnapshot snapshot(String projectId) {
        return new RecommendationCreationService.OwnerCheckedSnapshot(projectId, 7,
            "profile-1", 3, "confirmation-1", 2, 4, 5,
            Set.of("profile.features"), Map.of("profile.features", "[\"document search\"]"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
    }

    private static RecommendationCreationService.CapabilityAssessment assessment() {
        var claims = CapabilityKey.keys().stream().sorted().map(key ->
            new AiCapabilityIntake.Claim(key,
                key.equals("cap_document_reference") ? AiCapabilityIntake.Need.REQUIRED
                    : AiCapabilityIntake.Need.OPTIONAL,
                "Based on confirmed project profile", List.of("profile.features"))).toList();
        return new RecommendationCreationService.CapabilityAssessment(claims, List.of());
    }
}
