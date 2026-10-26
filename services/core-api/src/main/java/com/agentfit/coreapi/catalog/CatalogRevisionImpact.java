package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;
import com.agentfit.coreapi.recommendation.selection.PermissionMapping;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Conservative review impact between two hash-verified, semantically parsed Catalogs. */
public final class CatalogRevisionImpact {
    private CatalogRevisionImpact() {}

    public record Result(boolean revisionChanged, Set<String> toolsNeedingReview,
                         Set<VerifiedCombination> combinationsNeedingReview) {
        public Result {
            toolsNeedingReview = Set.copyOf(toolsNeedingReview);
            combinationsNeedingReview = Set.copyOf(combinationsNeedingReview);
        }
    }

    public static Result compare(VerifiedCatalogBundle before, VerifiedCatalogBundle after) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        return compare(CatalogSemanticParser.parse(before), CatalogSemanticParser.parse(after),
            before.files(), after.files());
    }

    static Result compare(ParsedCatalog before, ParsedCatalog after,
                          Map<String, String> beforeFiles, Map<String, String> afterFiles) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        Objects.requireNonNull(beforeFiles);
        Objects.requireNonNull(afterFiles);
        Set<String> allTools = new TreeSet<>(before.release().tools().keySet());
        allTools.addAll(after.release().tools().keySet());
        Set<String> affected = new TreeSet<>();
        for (String key : allTools) {
            CatalogTool previous = before.release().tools().get(key);
            CatalogTool current = after.release().tools().get(key);
            if (!Objects.equals(previous, current)
                || !Objects.equals(before.riskReviews().getOrDefault(key, ToolRiskReview.unassessed()),
                    after.riskReviews().getOrDefault(key, ToolRiskReview.unassessed()))
                || !Objects.equals(permissions(before, key), permissions(after, key))
                || !Objects.equals(supportEvidence(before, previous),
                    supportEvidence(after, current))) {
                affected.add(key);
            }
        }
        if (!Objects.equals(beforeFiles.get("capabilities.json"), afterFiles.get("capabilities.json"))
            || !Objects.equals(beforeFiles.get("client-capabilities.json"),
                afterFiles.get("client-capabilities.json"))
            || !outputs(beforeFiles).equals(outputs(afterFiles))) {
            affected.addAll(allTools);
        }

        Set<VerifiedCombination> allCombinations = new HashSet<>(
            before.release().verifiedCombinations());
        allCombinations.addAll(after.release().verifiedCombinations());
        Set<VerifiedCombination> combinations = new HashSet<>();
        for (VerifiedCombination combination : allCombinations) {
            if (!before.release().verifiedCombinations().contains(combination)
                || !after.release().verifiedCombinations().contains(combination)
                || !Objects.equals(before.evidence().get(combinationEvidenceKey(combination)),
                    after.evidence().get(combinationEvidenceKey(combination)))
                || !java.util.Collections.disjoint(combination.toolKeys(), affected)) {
                combinations.add(combination);
            }
        }
        boolean revisionChanged = !Objects.equals(before.catalogHash(), after.catalogHash())
            || !before.release().equals(after.release())
            || !before.permissionMappings().equals(after.permissionMappings())
            || !before.evidence().equals(after.evidence())
            || !before.riskReviews().equals(after.riskReviews())
            || !beforeFiles.equals(afterFiles);
        return new Result(revisionChanged, affected, combinations);
    }

    private static Map<String, PermissionReview> permissions(ParsedCatalog catalog, String toolKey) {
        Map<String, PermissionReview> result = new HashMap<>();
        for (PermissionMapping mapping : catalog.permissionMappings()) {
            if (toolKey.equals(mapping.toolKey())) {
                result.put(mapping.mappingKey(), new PermissionReview(mapping,
                    catalog.evidence().get("permission:" + toolKey + ":" + mapping.mappingKey())));
            }
        }
        return result;
    }

    private record PermissionReview(PermissionMapping mapping, VerificationEvidence evidence) {}

    private static Map<String, VerificationEvidence> supportEvidence(ParsedCatalog catalog,
                                                                       CatalogTool tool) {
        Map<String, VerificationEvidence> result = new HashMap<>();
        if (tool != null) {
            tool.support().forEach(row -> result.put(row.key(),
                catalog.evidence().get("support:" + row.key())));
        }
        return result;
    }

    private static Map<String, String> outputs(Map<String, String> files) {
        Map<String, String> result = new HashMap<>();
        files.forEach((path, content) -> {
            if (path.startsWith("templates/") || path.startsWith("guides/")) {
                result.put(path, content);
            }
        });
        return result;
    }

    private static String combinationEvidenceKey(VerifiedCombination combination) {
        var target = combination.target();
        return "combination:" + target.osFamily() + ":" + target.clientId() + ":"
            + target.clientVersion() + ":"
            + String.join(",", combination.toolKeys().stream().sorted().toList());
    }
}
