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
import java.util.concurrent.atomic.AtomicReference;
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
            aiInput -> {
                assertEquals(Set.of("aiSourceValues"), java.util.Arrays.stream(
                    aiInput.getClass().getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)
                    .collect(java.util.stream.Collectors.toSet()));
                assertEquals("[\"document search\"]", aiInput.aiSourceValues().get("profile.item.features.0"));
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

        var created = service.create("project-1", expected());

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

        assertThrows(IllegalArgumentException.class, () -> service.create("project-1", expected()));
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

        assertThrows(IllegalStateException.class, () -> service.create("project-1", expected()));
    }

    @Test
    void newAiFailureDoesNotReplacePreviouslySuccessfulRecommendation() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        AtomicReference<String> previousRecommendation = new AtomicReference<>("recommendation-success-1");
        var service = new RecommendationCreationService(
            RecommendationCreationServiceTest::snapshot,
            snapshot -> { throw new IllegalStateException("AI provider timeout"); },
            () -> new RecommendationCreationService.ApprovedCatalog(directory, approvedHash),
            (snapshot, basis, result) -> {
                previousRecommendation.set("changed");
                return "recommendation-failure";
            });

        assertThrows(IllegalStateException.class, () -> service.create("project-1", expected()));
        assertEquals("recommendation-success-1", previousRecommendation.get());
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
            () -> service.create("project-1", expected()));
    }

    @Test
    void persistsInformationRequestWhenEnvironmentAndInstallInventoryAreMissing() throws Exception {
        String approvedHash = SyntheticCatalogBundle.write(directory);
        AtomicInteger saves = new AtomicInteger();
        var service = new RecommendationCreationService(
            projectId -> new RecommendationCreationService.OwnerCheckedSnapshot(projectId, 7,
                "profile-1", 3, "confirmation-1", 2, 4, 0,
                Set.of("profile.item.features.0"), Map.of("profile.item.features.0", "[\"document search\"]"),
                false, null, null),
            snapshot -> assessment(),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, approvedHash),
            (snapshot, basis, result) -> {
                assertEquals(RecommendationDecision.Status.NEEDS_INFORMATION,
                    result.decision().status());
                assertEquals(List.of("environment_target", "installed_components"),
                    result.decision().questionCodes());
                assertTrue(result.items().isEmpty());
                saves.incrementAndGet();
                return "recommendation-1";
            });

        assertEquals(RecommendationDecision.Status.NEEDS_INFORMATION,
            service.create("project-1",
                new RecommendationCreationService.ExpectedVersions(7, 4, 0))
                .result().decision().status());
        assertEquals(1, saves.get());
    }

    @Test
    void rejectsUnconfirmedProjectVersionBeforeAnyAiRequest() throws Exception {
        AtomicInteger aiCalls = new AtomicInteger();
        var service = new RecommendationCreationService(
            projectId -> new RecommendationCreationService.OwnerCheckedSnapshot(projectId, 0,
                "profile-1", 3, "confirmation-1", 2, 4, 5,
                Set.of("profile.item.features.0"), Map.of("profile.item.features.0", "[\"document search\"]"),
                false, new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of()),
            snapshot -> { aiCalls.incrementAndGet(); return assessment(); },
            () -> fail("invalid snapshot must not read Catalog"),
            (snapshot, basis, result) -> fail("invalid snapshot must not be saved"));

        assertThrows(IllegalArgumentException.class, () -> service.create("project-1", expected()));
        assertEquals(0, aiCalls.get());
    }

    @Test
    void rejectsMalformedInstalledVersionBeforeAnyAiRequest() {
        assertThrows(IllegalArgumentException.class,
            () -> new RecommendationCreationService.OwnerCheckedSnapshot("project-1", 7,
                "profile-1", 3, "confirmation-1", 2, 4, 5,
                Set.of("profile.item.features.0"), Map.of("profile.item.features.0", "[\"document search\"]"),
                false, new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
                Map.of("example-tool", " ")));
    }

    @Test
    void rejectsExcludedPlanningFieldsBeforeTheyCanReachAi() {
        assertThrows(IllegalArgumentException.class,
            () -> new RecommendationCreationService.OwnerCheckedSnapshot("project-1", 7,
                "profile-1", 3, "confirmation-1", 2, 4, 5,
                Set.of("profile.item.purpose.0"),
                Map.of("profile.item.purpose.0", "legacy value"), false,
                new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of()));
    }

    @Test
    void rejectsStaleExpectedVersionsBeforeCatalogOrAi() {
        AtomicInteger aiCalls = new AtomicInteger();
        var service = new RecommendationCreationService(
            RecommendationCreationServiceTest::snapshot,
            snapshot -> { aiCalls.incrementAndGet(); return assessment(); },
            () -> fail("stale request must not read Catalog"),
            (snapshot, basis, result) -> fail("stale request must not be saved"));

        assertThrows(RecommendationCreationService.SnapshotChangedException.class,
            () -> service.create("project-1",
                new RecommendationCreationService.ExpectedVersions(6, 4, 5)));
        assertThrows(RecommendationCreationService.SnapshotChangedException.class,
            () -> service.create("project-1",
                new RecommendationCreationService.ExpectedVersions(7, 3, 5)));
        assertThrows(RecommendationCreationService.SnapshotChangedException.class,
            () -> service.create("project-1",
                new RecommendationCreationService.ExpectedVersions(7, 4, 4)));
        assertEquals(0, aiCalls.get());
    }

    @Test
    void rejectsMissingOrMalformedExpectedVersions() {
        assertThrows(IllegalArgumentException.class,
            () -> new RecommendationCreationService.ExpectedVersions(0, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new RecommendationCreationService.ExpectedVersions(1, -1, 0));
        var service = new RecommendationCreationService(
            projectId -> fail("invalid request must not read snapshot"),
            snapshot -> fail("invalid request must not call AI"),
            () -> fail("invalid request must not read Catalog"),
            (snapshot, basis, result) -> fail("invalid request must not save"));
        assertThrows(IllegalArgumentException.class,
            () -> service.create("project-1", null));
    }

    private static RecommendationCreationService.OwnerCheckedSnapshot snapshot(String projectId) {
        return new RecommendationCreationService.OwnerCheckedSnapshot(projectId, 7,
            "profile-1", 3, "confirmation-1", 2, 4, 5,
            Set.of("profile.item.features.0"), Map.of("profile.item.features.0", "[\"document search\"]"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
    }

    private static RecommendationCreationService.ExpectedVersions expected() {
        return new RecommendationCreationService.ExpectedVersions(7, 4, 5);
    }

    private static RecommendationCreationService.CapabilityAssessment assessment() {
        var claims = CapabilityKey.keys().stream().sorted().map(key ->
            new AiCapabilityIntake.Claim(key,
                key.equals("cap_document_reference") ? AiCapabilityIntake.Need.REQUIRED
                    : AiCapabilityIntake.Need.OPTIONAL,
                "Based on confirmed project profile", List.of("profile.item.features.0"))).toList();
        return new RecommendationCreationService.CapabilityAssessment(claims, List.of());
    }
}
