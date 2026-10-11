package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.persistence.RecommendationMetadataAssembler;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationLatestDetailsServiceTest {
    private static final EnvironmentTarget TARGET =
        new EnvironmentTarget("WINDOWS", "example-client", "1.0");
    @TempDir Path directory;

    @Test
    void enrichesCurrentStoredRecommendationFromExactApprovedCatalog() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var draft = storedRecommendation(hash);
        var service = service(draft.basis(), draft, () ->
            new RecommendationCreationService.ApprovedCatalog(directory, hash));

        var latest = service.latest(draft.basis().projectId());

        assertEquals(RecommendationReadModel.Validity.CURRENT, latest.summary().validity());
        assertEquals(9, latest.capabilities().size());
        assertEquals(1, latest.catalogItems().size());
        var item = latest.catalogItems().getFirst();
        assertEquals("example-tool", item.toolKey());
        assertEquals("support-1", item.supportKey());
        assertEquals("read", item.permissionOptions().getFirst().mappingKey());
        assertEquals(draft.items(), latest.storedItems());
    }

    @Test
    void staleRecommendationRemainsReadableWithoutLoadingNewCatalog() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var draft = storedRecommendation(hash);
        var changed = new PreviewBasis(draft.basis().projectId(), 2,
            draft.basis().confirmedProfileId(), 1, draft.basis().confirmationEventId(),
            0, 0, 1, "new-release", "b".repeat(64));
        AtomicInteger catalogReads = new AtomicInteger();
        var service = service(changed, draft, () -> {
            catalogReads.incrementAndGet();
            throw new AssertionError("stale result must not request current Catalog details");
        });

        var latest = service.latest(draft.basis().projectId());

        assertEquals(RecommendationReadModel.Validity.STALE, latest.summary().validity());
        assertEquals(draft.items(), latest.storedItems());
        assertTrue(latest.catalogItems().isEmpty());
        assertEquals(0, catalogReads.get());
    }

    @Test
    void currentRecommendationRejectsStoredSupportMismatch() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var original = storedRecommendation(hash);
        var item = original.items().getFirst();
        var forged = new JdbcRecommendationMetadataWriter.Draft(original.id(), original.basis(),
            original.status(), original.evidenceLevel(), original.createdAt(),
            original.capabilities(), List.of(new JdbcRecommendationMetadataWriter.Item(
                item.id(), item.toolKey(), item.toolVersion(), "forged-support", item.required(),
                item.reason(), item.explanationSource())), original.notices());
        var service = service(original.basis(), forged, () ->
            new RecommendationCreationService.ApprovedCatalog(directory, hash));

        assertThrows(IllegalArgumentException.class,
            () -> service.latest(original.basis().projectId()));
    }

    @Test
    void returnsNoRecommendationWithoutConsultingCatalog() throws Exception {
        var basis = new PreviewBasis("11111111-1111-1111-1111-111111111111", 1,
            "22222222-2222-2222-2222-222222222222", 1,
            "33333333-3333-3333-3333-333333333333", 0, 0, 1,
            "example-release", "a".repeat(64));
        var service = service(basis, null, () -> {
            throw new AssertionError("empty latest must not load Catalog");
        });

        assertNull(service.latest(basis.projectId()));
    }

    @Test
    void rejectsCurrentResultWhenIndependentCatalogApprovalChanged() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var draft = storedRecommendation(hash);
        var service = service(draft.basis(), draft, () ->
            new RecommendationCreationService.ApprovedCatalog(directory, "b".repeat(64)));

        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> service.latest(draft.basis().projectId()));
    }

    private RecommendationLatestDetailsService service(PreviewBasis current,
        JdbcRecommendationMetadataWriter.Draft stored,
        RecommendationCreationService.ApprovedCatalogReader catalog) {
        return new RecommendationLatestDetailsService(
            projectId -> new RecommendationLatestDetailsService.Current(current, TARGET),
            projectId -> stored, catalog);
    }

    private JdbcRecommendationMetadataWriter.Draft storedRecommendation(String hash) {
        var claims = CapabilityKey.keys().stream().sorted().map(key ->
            new AiCapabilityIntake.Claim(key,
                key.equals("cap_document_reference") ? AiCapabilityIntake.Need.REQUIRED
                    : AiCapabilityIntake.Need.OPTIONAL,
                "confirmed requirement", List.of("profile.features"))).toList();
        var result = CatalogRecommendationWorkflow.evaluate(directory, hash,
            new CatalogRecommendationWorkflow.Request(claims, List.of(),
                Set.of("profile.features"), false, TARGET, Map.of()));
        var basis = new PreviewBasis("11111111-1111-1111-1111-111111111111", 1,
            "22222222-2222-2222-2222-222222222222", 1,
            "33333333-3333-3333-3333-333333333333", 0, 0, 1,
            result.catalogReleaseId(), hash);
        return RecommendationMetadataAssembler.assemble(UUID.randomUUID().toString(), basis,
            result, JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.parse("2026-10-12T00:00:00Z"), List.of(), () -> UUID.randomUUID().toString());
    }
}
