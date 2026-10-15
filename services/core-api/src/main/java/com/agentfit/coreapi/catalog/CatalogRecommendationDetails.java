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
import java.util.Set;

/** Projects only selected, exactly supported Catalog rows into an internal recommendation result. */
public final class CatalogRecommendationDetails {
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
                       VerificationEvidence supportEvidence,
                       List<PermissionOption> permissionOptions) {
        public Item {
            coveredRequiredCapabilities = Set.copyOf(coveredRequiredCapabilities);
            permissionOptions = List.copyOf(permissionOptions);
        }
    }

    static List<Item> project(ParsedCatalog catalog, RecommendationDecision decision,
                              Set<String> requiredCapabilities, EnvironmentTarget target) {
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
            items.add(new Item(key, tool.name(), tool.kind(), tool.sourceUrl(),
                tool.version(), support.key(), covered,
                supportEvidence, permissions));
        }
        return List.copyOf(items);
    }

    private static CatalogBundleLoader.CatalogUnavailableException unavailable(String reason) {
        return new CatalogBundleLoader.CatalogUnavailableException(reason);
    }
}
