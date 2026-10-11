package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.persistence.RecommendationMetadataAssembler;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationMetadataAssemblerTest {
    @TempDir Path directory;

    @Test
    void projectsValidatedClaimsAndRequiredCatalogItemsForAtomicSave() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var result = recommendation(hash);
        var draft = RecommendationMetadataAssembler.assemble(UUID.randomUUID().toString(),
            basis(hash), result, JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.parse("2026-10-12T00:00:00Z"), List.of(), () -> UUID.randomUUID().toString());

        assertEquals(9, draft.capabilities().size());
        assertEquals(JdbcRecommendationMetadataWriter.Necessity.REQUIRED,
            draft.capabilities().stream().filter(c -> c.key().equals("cap_document_reference"))
                .findFirst().orElseThrow().necessity());
        assertEquals(List.of("profile.features"), draft.capabilities().getFirst().inputRefs());
        assertEquals(1, draft.items().size());
        var item = draft.items().getFirst();
        assertEquals("example-tool", item.toolKey());
        assertEquals("1.0", item.toolVersion());
        assertEquals("support-1", item.supportKey());
        assertTrue(item.required());
        assertEquals(JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE,
            item.explanationSource());
    }

    @Test
    void rejectsCatalogBasisMismatchAndForgedItemSet() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var result = recommendation(hash);
        assertThrows(IllegalArgumentException.class, () -> RecommendationMetadataAssembler.assemble(
            UUID.randomUUID().toString(), basis("b".repeat(64)), result,
            JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.now(), List.of(), () -> UUID.randomUUID().toString()));
        var forged = new CatalogRecommendationWorkflow.Result(result.catalogReleaseId(),
            result.catalogHash(), result.decision(), result.capabilities(),
            result.clarificationQuestions(), List.of());
        assertThrows(IllegalArgumentException.class, () -> RecommendationMetadataAssembler.assemble(
            UUID.randomUUID().toString(), basis(hash), forged,
            JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.now(), List.of(), () -> UUID.randomUUID().toString()));
    }

    @Test
    void keepsNeedsInformationFreeOfRecommendedItems() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var request = new CatalogRecommendationWorkflow.Request(claims(), List.of(),
            Set.of("profile.features"), false, null, null);
        var result = CatalogRecommendationWorkflow.evaluate(directory, hash, request);
        assertEquals(RecommendationDecision.Status.NEEDS_INFORMATION, result.decision().status());
        var question = new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
            JdbcRecommendationMetadataWriter.NoticeType.QUESTION,
            JdbcRecommendationMetadataWriter.QuestionOwner.B, "environment.target",
            "environment_target", "개발 환경을 확인해 주세요", List.of());
        var installedQuestion = new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
            JdbcRecommendationMetadataWriter.NoticeType.QUESTION,
            JdbcRecommendationMetadataWriter.QuestionOwner.B, "environment.installedComponents",
            "installed_components", "현재 설치된 도구를 확인해 주세요", List.of());
        var draft = RecommendationMetadataAssembler.assemble(UUID.randomUUID().toString(),
            basis(hash), result, JdbcRecommendationMetadataWriter.EvidenceLevel.PLAN_ONLY,
            Instant.now(), List.of(question, installedQuestion), () -> UUID.randomUUID().toString());
        assertTrue(draft.items().isEmpty());
        assertEquals(List.of(question, installedQuestion), draft.notices());
    }

    @Test
    void refusesToDropARequiredInformationQuestionDuringStorageProjection() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var result = CatalogRecommendationWorkflow.evaluate(directory, hash,
            new CatalogRecommendationWorkflow.Request(claims(), List.of(),
                Set.of("profile.features"), false, null, null));
        assertEquals(List.of("environment_target", "installed_components"),
            result.decision().questionCodes());
        var incompleteQuestion = new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
            JdbcRecommendationMetadataWriter.NoticeType.QUESTION,
            JdbcRecommendationMetadataWriter.QuestionOwner.B, "environment.target",
            "environment_target", "개발 환경을 확인해 주세요", List.of());

        assertThrows(IllegalArgumentException.class, () -> RecommendationMetadataAssembler.assemble(
            UUID.randomUUID().toString(), basis(hash), result,
            JdbcRecommendationMetadataWriter.EvidenceLevel.PLAN_ONLY, Instant.now(),
            List.of(incompleteQuestion), () -> UUID.randomUUID().toString()));
    }

    @Test
    void createsBReasonForNoAdditionsWithoutCallerWrittenExplanation() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var result = CatalogRecommendationWorkflow.evaluate(directory, hash,
            new CatalogRecommendationWorkflow.Request(claims(null), List.of(),
                Set.of("profile.features"), false,
                new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of()));

        var draft = RecommendationMetadataAssembler.assemble(UUID.randomUUID().toString(),
            basis(hash), result, JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.now(), List.of(), () -> UUID.randomUUID().toString());

        assertEquals(RecommendationDecision.Status.NO_ADDITIONS_NEEDED, draft.status());
        assertTrue(draft.items().isEmpty());
        assertEquals(List.of("no_required_capabilities"),
            draft.notices().stream().map(JdbcRecommendationMetadataWriter.Notice::code).toList());
        assertEquals(JdbcRecommendationMetadataWriter.NoticeType.REASON,
            draft.notices().getFirst().type());
        assertFalse(draft.notices().getFirst().message().isBlank());
    }

    @Test
    void createsBReasonForMissingVerifiedCapabilitySupport() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var result = CatalogRecommendationWorkflow.evaluate(directory, hash,
            new CatalogRecommendationWorkflow.Request(claims("cap_ai_evaluation"), List.of(),
                Set.of("profile.features"), false,
                new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of()));

        var draft = RecommendationMetadataAssembler.assemble(UUID.randomUUID().toString(),
            basis(hash), result, JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.now(), List.of(), () -> UUID.randomUUID().toString());

        assertEquals(RecommendationDecision.Status.NO_COMPATIBLE_TOOLS, draft.status());
        assertEquals(List.of("no_verified_capability_support"),
            draft.notices().stream().map(JdbcRecommendationMetadataWriter.Notice::code).toList());
    }

    private CatalogRecommendationWorkflow.Result recommendation(String hash) {
        return CatalogRecommendationWorkflow.evaluate(directory, hash,
            new CatalogRecommendationWorkflow.Request(claims(), List.of(),
                Set.of("profile.features"), false,
                new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of()));
    }

    private static List<AiCapabilityIntake.Claim> claims() {
        return claims("cap_document_reference");
    }

    private static List<AiCapabilityIntake.Claim> claims(String requiredKey) {
        return CapabilityKey.keys().stream().sorted().map(key -> new AiCapabilityIntake.Claim(key,
            key.equals(requiredKey) ? AiCapabilityIntake.Need.REQUIRED
                : AiCapabilityIntake.Need.OPTIONAL,
            "Confirmed profile requirement", List.of("profile.features"))).toList();
    }

    private static PreviewBasis basis(String hash) {
        return new PreviewBasis("11111111-1111-1111-1111-111111111111", 1,
            "22222222-2222-2222-2222-222222222222", 1,
            "33333333-3333-3333-3333-333333333333", 0, 0, 1,
            "synthetic-recommendation", hash);
    }
}
