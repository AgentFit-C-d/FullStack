package com.agentfit.coreapi.recommendation;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.CatalogValidator;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;

import static com.agentfit.coreapi.recommendation.RecommendationDecision.Status.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Pure B decision logic. The caller owns authentication, A/AI contracts, and persistence. */
public final class RecommendationEngine {
    private final CatalogRelease release;

    public RecommendationEngine(CatalogRelease release) {
        CatalogValidator.validate(release);
        this.release = release;
    }

    public RecommendationDecision decide(RecommendationInput input) {
        if (!CapabilityKey.keys().containsAll(input.requiredCapabilityKeys())) {
            throw new IllegalArgumentException("unknown required capability ID");
        }
        List<String> questions = new ArrayList<>();
        if (input.hasRelevantPendingConflict()) questions.add("pending_conflict");
        if (input.hasRelevantUndeterminedCapability()) questions.add("undetermined_capability");
        if (!input.requiredCapabilityKeys().isEmpty()) {
            if (input.environment() == null || !input.environment().complete()) {
                questions.add("environment_target");
            }
            if (input.installedToolVersions() == null) {
                questions.add("installed_components");
            } else if (input.installedToolVersions().entrySet().stream().anyMatch(entry ->
                entry.getValue() == null && release.tools().containsKey(entry.getKey())
                    && !java.util.Collections.disjoint(
                        release.tools().get(entry.getKey()).capabilityKeys(), input.requiredCapabilityKeys()))) {
                questions.add("installed_component_version");
            }
        }
        if (!questions.isEmpty()) {
            return new RecommendationDecision(NEEDS_INFORMATION, List.of(), questions, List.of());
        }
        if (input.requiredCapabilityKeys().isEmpty()) {
            return new RecommendationDecision(NO_ADDITIONS_NEEDED, List.of(), List.of(),
                List.of("no_required_capabilities"));
        }

        // Unknown installed names are user declarations, not Catalog support evidence.
        Set<String> installed = new HashSet<>();
        Set<String> available = new TreeSet<>();
        for (String key : release.tools().keySet()) {
            if (supports(key, input.environment())) available.add(key);
        }
        for (var entry : input.installedToolVersions().entrySet()) {
            CatalogTool tool = release.tools().get(entry.getKey());
            if (tool != null && tool.version().equals(entry.getValue()) && available.contains(tool.key())) {
                installed.add(tool.key());
            }
        }
        if (validSelection(installed, available, input.environment())
            && capabilities(installed).containsAll(input.requiredCapabilityKeys())) {
            return new RecommendationDecision(NO_ADDITIONS_NEEDED, List.of(), List.of(),
                List.of("installed_components_cover_required"));
        }
        if (!capabilities(available).containsAll(input.requiredCapabilityKeys())) {
            return new RecommendationDecision(NO_COMPATIBLE_TOOLS, List.of(), List.of(),
                List.of("no_verified_capability_support"));
        }

        List<String> candidates = available.stream().filter(key -> !installed.contains(key)).toList();
        // A single addition is linear even when the Catalog has many unrelated tools.
        List<String> single = search(candidates, 1, 0, new ArrayList<>(),
            installed, available, input.requiredCapabilityKeys(), input.environment());
        if (single != null) {
            return new RecommendationDecision(RECOMMENDED, single, List.of(), List.of());
        }
        // MVP catalog target is about 15–20 entries; bound combinatorial search until a
        // larger catalog has a dedicated solver and performance budget.
        if (candidates.size() > 20) {
            throw new CatalogValidator.InvalidCatalogException("catalog exceeds planner limit");
        }
        for (int size = 2; size <= candidates.size(); size++) {
            List<String> chosen = search(candidates, size, 0, new ArrayList<>(),
                installed, available, input.requiredCapabilityKeys(), input.environment());
            if (chosen != null) {
                return new RecommendationDecision(RECOMMENDED, chosen, List.of(), List.of());
            }
        }
        return new RecommendationDecision(NO_COMPATIBLE_TOOLS, List.of(), List.of(),
            List.of("no_verified_compatible_combination"));
    }

    private List<String> search(List<String> candidates, int count, int start,
                                List<String> chosen, Set<String> installed,
                                Set<String> available, Set<String> required, EnvironmentTarget target) {
        if (chosen.size() == count) {
            Set<String> all = new HashSet<>(installed);
            all.addAll(chosen);
            return validSelection(all, available, target) && capabilities(all).containsAll(required)
                ? List.copyOf(chosen) : null;
        }
        for (int i = start; i <= candidates.size() - (count - chosen.size()); i++) {
            chosen.add(candidates.get(i));
            List<String> found = search(candidates, count, i + 1, chosen, installed, available,
                required, target);
            if (found != null) return found;
            chosen.remove(chosen.size() - 1);
        }
        return null;
    }

    private boolean supports(String key, EnvironmentTarget target) {
        return release.tools().get(key).support().stream().anyMatch(support -> support.verifiedFor(target));
    }

    private Set<String> capabilities(Set<String> keys) {
        Set<String> result = new HashSet<>();
        for (String key : keys) result.addAll(release.tools().get(key).capabilityKeys());
        return result;
    }

    private boolean validSelection(Set<String> selected, Set<String> available,
                                   EnvironmentTarget target) {
        if (!available.containsAll(selected)) return false;
        Set<String> components = new HashSet<>();
        for (String key : selected) {
            CatalogTool tool = release.tools().get(key);
            if (!selected.containsAll(tool.dependencyKeys())) return false;
            for (String conflict : tool.conflictKeys()) {
                if (selected.contains(conflict)) return false;
            }
            for (String component : tool.includedComponentKeys()) {
                if (!components.add(component)) return false;
            }
        }
        return selected.size() < 2 || release.verifiedCombinations()
            .contains(new VerifiedCombination(selected, target));
    }
}
