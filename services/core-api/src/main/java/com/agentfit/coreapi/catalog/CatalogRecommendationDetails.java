package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.selection.PermissionMapping;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects only selected, exactly supported Catalog rows into an internal recommendation result. */
public final class CatalogRecommendationDetails {
    private static final Map<String, String> CAPABILITY_NAMES = Map.ofEntries(
        Map.entry("cap_document_reference", "문서 참조"),
        Map.entry("cap_repository_navigation", "저장소 탐색"),
        Map.entry("cap_code_review", "코드 검토"),
        Map.entry("cap_test_authoring", "테스트 작성"),
        Map.entry("cap_test_execution", "테스트 실행"),
        Map.entry("cap_browser_verification", "브라우저 검증"),
        Map.entry("cap_api_verification", "API 검증"),
        Map.entry("cap_database_schema_inspection", "DB 스키마 확인"),
        Map.entry("cap_ai_evaluation", "AI 평가"));

    private CatalogRecommendationDetails() {}

    public record PermissionOption(String mappingKey, boolean required,
                                   Set<PermissionPolicy> supportedPolicies,
                                   PermissionPolicy defaultPolicy,
                                   VerificationEvidence evidence) {
        public PermissionOption { supportedPolicies = Set.copyOf(supportedPolicies); }
    }

    public record Item(String toolKey, String name, CatalogTool.Kind kind, String sourceUrl,
                       String catalogVersion, String supportKey,
                       Set<String> coveredRequiredCapabilities,
                       Set<String> coveredOptionalCapabilities, boolean required,
                       VerificationEvidence supportEvidence,
                       List<PermissionOption> permissionOptions, String reason,
                       ToolRiskReview riskReview) {
        public Item {
            coveredRequiredCapabilities = Set.copyOf(coveredRequiredCapabilities);
            coveredOptionalCapabilities = Set.copyOf(coveredOptionalCapabilities);
            permissionOptions = List.copyOf(permissionOptions);
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("recommendation reason required");
            }
            if (riskReview == null) throw new IllegalArgumentException("risk review status required");
        }

        public Item(String toolKey, String name, CatalogTool.Kind kind, String sourceUrl,
                    String catalogVersion, String supportKey,
                    Set<String> coveredRequiredCapabilities,
                    Set<String> coveredOptionalCapabilities, boolean required,
                    VerificationEvidence supportEvidence,
                    List<PermissionOption> permissionOptions, String reason) {
            this(toolKey, name, kind, sourceUrl, catalogVersion, supportKey,
                coveredRequiredCapabilities, coveredOptionalCapabilities, required,
                supportEvidence, permissionOptions, reason, ToolRiskReview.unassessed());
        }
    }

    static List<Item> project(ParsedCatalog catalog, RecommendationDecision decision,
                              Set<String> requiredCapabilities, EnvironmentTarget target) {
        return project(catalog, decision, requiredCapabilities, Set.of(),
            Set.copyOf(decision.toolKeys()), target);
    }

    static List<Item> project(ParsedCatalog catalog, RecommendationDecision decision,
                              Set<String> requiredCapabilities, Set<String> optionalCapabilities,
                              Set<String> requiredToolKeys, EnvironmentTarget target) {
        if (decision.status() != RecommendationDecision.Status.RECOMMENDED) return List.of();
        List<Item> items = new ArrayList<>();
        for (String key : decision.toolKeys()) {
            CatalogTool tool = catalog.release().tools().get(key);
            if (tool == null || target == null) throw unavailable("selected tool or target missing");
            ToolSupport support = tool.support().stream().filter(row -> row.verifiedFor(target))
                .findFirst().orElseThrow(() -> unavailable("selected tool support missing"));
            VerificationEvidence supportEvidence = catalog.evidence().get("support:" + support.key());
            if (supportEvidence == null) throw unavailable("selected support evidence missing");
            List<PermissionOption> permissions = new ArrayList<>();
            for (PermissionMapping mapping : catalog.permissionMappings().stream()
                .filter(row -> key.equals(row.toolKey()))
                .sorted(Comparator.comparing(PermissionMapping::mappingKey)).toList()) {
                VerificationEvidence evidence = catalog.evidence().get(
                    "permission:" + key + ":" + mapping.mappingKey());
                if (evidence == null || !mapping.supportedPolicies().contains(PermissionPolicy.ASK_EACH_TIME)) {
                    throw unavailable("selected permission evidence missing");
                }
                permissions.add(new PermissionOption(mapping.mappingKey(), mapping.required(),
                    mapping.supportedPolicies(), PermissionPolicy.ASK_EACH_TIME, evidence));
            }
            Set<String> covered = tool.capabilityKeys().stream()
                .filter(requiredCapabilities::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> coveredOptional = tool.capabilityKeys().stream()
                .filter(optionalCapabilities::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            boolean required = requiredToolKeys.contains(key);
            items.add(new Item(key, tool.name(), tool.kind(), tool.sourceUrl(),
                tool.version(), support.key(), required ? covered : Set.of(),
                coveredOptional, required, supportEvidence, permissions,
                required ? reason(covered) : optionalReason(coveredOptional),
                catalog.riskReviews().getOrDefault(key, ToolRiskReview.unassessed())));
        }
        return List.copyOf(items);
    }

    private static String optionalReason(Set<String> covered) {
        if (covered.isEmpty()) throw unavailable("optional item has no explicit optional capability");
        List<String> names = covered.stream().sorted().map(CAPABILITY_NAMES::get).toList();
        String summary = String.join("·", names.subList(0, Math.min(2, names.size())));
        if (names.size() > 2) summary += " 외 " + (names.size() - 2) + "개";
        return summary + " 기능을 위한 선택 도구이며 기본 구성에 자동 포함되지 않습니다.";
    }

    private static String reason(Set<String> covered) {
        if (covered.isEmpty()) {
            return "검증된 도구 조합의 의존성을 충족하기 위해 포함됩니다.";
        }
        List<String> names = covered.stream().sorted().map(CAPABILITY_NAMES::get).toList();
        String summary = String.join("·", names.subList(0, Math.min(2, names.size())));
        if (names.size() > 2) summary += " 외 " + (names.size() - 2) + "개";
        return summary + " 기능을 지원하며 현재 개발 환경에 맞는 카탈로그 지원 기록이 있습니다.";
    }

    private static CatalogBundleLoader.CatalogUnavailableException unavailable(String reason) {
        return new CatalogBundleLoader.CatalogUnavailableException(reason);
    }
}
