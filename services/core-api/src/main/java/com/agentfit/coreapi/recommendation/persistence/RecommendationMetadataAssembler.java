package com.agentfit.coreapi.recommendation.persistence;

import com.agentfit.coreapi.catalog.CatalogRecommendationDetails;
import com.agentfit.coreapi.catalog.CatalogRecommendationWorkflow;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Projects validated claims, selected tools, and B-owned verdict reasons into B1-B4 metadata. */
public final class RecommendationMetadataAssembler {
    private RecommendationMetadataAssembler() {}

    public static JdbcRecommendationMetadataWriter.Draft assemble(
        String recommendationId, PreviewBasis basis, CatalogRecommendationWorkflow.Result result,
        JdbcRecommendationMetadataWriter.EvidenceLevel evidenceLevel, Instant createdAt,
        List<JdbcRecommendationMetadataWriter.Notice> notices, Supplier<String> childIds) {
        Objects.requireNonNull(basis);
        Objects.requireNonNull(result);
        Objects.requireNonNull(result.decision());
        Objects.requireNonNull(childIds);
        if (!basis.catalogReleaseId().equals(result.catalogReleaseId())
            || !basis.catalogHash().equals(result.catalogHash())) {
            throw new IllegalArgumentException("recommendation Catalog differs from stored basis");
        }
        RecommendationDecision decision = result.decision();
        Set<String> decisionKeys = new HashSet<>(decision.toolKeys());
        Set<String> detailKeys = new HashSet<>();
        List<JdbcRecommendationMetadataWriter.Item> items = new ArrayList<>();
        for (CatalogRecommendationDetails.Item item : result.items()) {
            if (item == null || !detailKeys.add(item.toolKey())
                || item.supportEvidence() == null) {
                throw new IllegalArgumentException("invalid verified recommendation item");
            }
            items.add(new JdbcRecommendationMetadataWriter.Item(childIds.get(), item.toolKey(),
                item.catalogVersion(), item.supportKey(), item.required(),
                item.reason(),
                JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE,
                item.riskReview()));
        }
        if (decisionKeys.size() != decision.toolKeys().size()
            || !detailKeys.equals(decisionKeys)
            || decision.status() != RecommendationDecision.Status.RECOMMENDED
                && !items.isEmpty()) {
            throw new IllegalArgumentException("recommendation items differ from decision");
        }
        List<JdbcRecommendationMetadataWriter.Capability> capabilities = new ArrayList<>();
        for (AiCapabilityIntake.Claim claim : result.capabilities()) {
            if (claim == null || claim.need() == null) {
                throw new IllegalArgumentException("invalid Capability claim");
            }
            capabilities.add(new JdbcRecommendationMetadataWriter.Capability(
                claim.capabilityKey(),
                JdbcRecommendationMetadataWriter.Necessity.valueOf(claim.need().name()),
                claim.reason(), claim.sourceFields()));
        }
        List<JdbcRecommendationMetadataWriter.Notice> completeNotices = new ArrayList<>(notices);
        for (String code : decision.questionCodes()) {
            if (!isBQuestion(code)) continue;
            if (completeNotices.stream().anyMatch(notice -> notice != null
                && code.equals(notice.code()))) {
                throw new IllegalArgumentException("B question cannot be overridden");
            }
            completeNotices.add(new JdbcRecommendationMetadataWriter.Notice(childIds.get(),
                JdbcRecommendationMetadataWriter.NoticeType.QUESTION,
                JdbcRecommendationMetadataWriter.QuestionOwner.B,
                questionTarget(code), code, questionMessage(code), List.of()));
        }
        Set<String> reasonCodes = new HashSet<>();
        for (String code : decision.reasonCodes()) {
            if (!reasonCodes.add(code) || completeNotices.stream().anyMatch(notice ->
                notice != null && notice.type() == JdbcRecommendationMetadataWriter.NoticeType.REASON
                    && code.equals(notice.code()))) {
                throw new IllegalArgumentException("duplicate recommendation reason");
            }
            completeNotices.add(new JdbcRecommendationMetadataWriter.Notice(childIds.get(),
                JdbcRecommendationMetadataWriter.NoticeType.REASON, null, null, code,
                reasonMessage(code), List.of()));
        }
        Set<String> questionCodes = new HashSet<>();
        for (JdbcRecommendationMetadataWriter.Notice notice : completeNotices) {
            if (notice != null && notice.type() == JdbcRecommendationMetadataWriter.NoticeType.QUESTION) {
                questionCodes.add(notice.code());
            }
        }
        if (new HashSet<>(decision.questionCodes()).size() != decision.questionCodes().size()
            || !questionCodes.containsAll(decision.questionCodes())
            || decision.status() != RecommendationDecision.Status.NEEDS_INFORMATION
                && !decision.questionCodes().isEmpty()) {
            throw new IllegalArgumentException("recommendation questions differ from decision");
        }
        var draft = new JdbcRecommendationMetadataWriter.Draft(recommendationId, basis,
            decision.status(), evidenceLevel, createdAt, List.copyOf(capabilities),
            List.copyOf(items), List.copyOf(completeNotices));
        JdbcRecommendationMetadataWriter.validate(draft);
        return draft;
    }

    private static boolean isBQuestion(String code) {
        return "environment_target".equals(code) || "installed_components".equals(code)
            || "installed_component_version".equals(code);
    }

    private static String questionTarget(String code) {
        return switch (code) {
            case "environment_target" -> "environment.target";
            case "installed_components" -> "environment.installedComponents";
            case "installed_component_version" -> "environment.installedComponentVersions";
            default -> throw new IllegalArgumentException("unknown B question");
        };
    }

    private static String questionMessage(String code) {
        return switch (code) {
            case "environment_target" -> "사용 중인 OS와 AI 클라이언트 및 버전을 확인해 주세요.";
            case "installed_components" -> "현재 설치된 도구 목록을 확인해 주세요.";
            case "installed_component_version" -> "현재 설치된 도구의 버전을 확인해 주세요.";
            default -> throw new IllegalArgumentException("unknown B question");
        };
    }

    private static String reasonMessage(String code) {
        return switch (code) {
            case "no_required_capabilities" -> "확인된 필수 기능이 없어 추가 도구가 필요하지 않습니다.";
            case "installed_components_cover_required" ->
                "현재 설치된 검증 도구가 필수 기능을 충족합니다.";
            case "no_verified_capability_support" ->
                "필수 기능을 지원하는 검증된 도구가 없습니다.";
            case "no_verified_compatible_combination" ->
                "필수 기능을 모두 충족하는 검증된 도구 조합이 없습니다.";
            default -> throw new IllegalArgumentException("unknown B recommendation reason");
        };
    }
}
