package com.agentfit.coreapi.recommendation;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.CatalogValidator;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;

import static com.agentfit.coreapi.recommendation.RecommendationDecision.Status.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Pure B decision logic. The caller owns authentication, A/AI contracts, and persistence. */
public final class RecommendationEngine {
    private final CatalogRelease release;

    public RecommendationEngine(CatalogRelease release) {
        CatalogValidator.validate(release);
        this.release = release;
    }

    public RecommendationDecision decide(RecommendationInput input) {
        return decide(input, keys -> true);
    }

    public RecommendationDecision decide(RecommendationInput input,
                                         Predicate<List<String>> configurationFits) {
        if (configurationFits == null) throw new IllegalArgumentException("configuration check required");
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
                entry.getValue() == null && relevantUnknownInstalledVersion(entry.getKey(),
                    input.requiredCapabilityKeys(), input.environment()))) {
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
        for (String key : candidates) {
            Set<String> selected = new HashSet<>(installed);
            selected.add(key);
            if (validSelection(selected, available, input.environment())
                && capabilities(selected).containsAll(input.requiredCapabilityKeys())
                && configurationFits.test(List.of(key))) {
                return new RecommendationDecision(RECOMMENDED, List.of(key), List.of(), List.of());
            }
        }
        List<List<String>> reviewedAdditions = new ArrayList<>();
        for (VerifiedCombination combination : release.verifiedCombinations()) {
            if (!combination.target().equals(input.environment())
                || !combination.toolKeys().containsAll(installed)
                || !available.containsAll(combination.toolKeys())
                || !capabilities(combination.toolKeys()).containsAll(input.requiredCapabilityKeys())
                || !validSelection(combination.toolKeys(), available, input.environment())) continue;
            List<String> missing = combination.toolKeys().stream()
                .filter(key -> !installed.contains(key)).sorted().toList();
            if (!missing.isEmpty()) reviewedAdditions.add(missing);
        }
        reviewedAdditions.sort(Comparator.comparingInt((List<String> keys) -> keys.size())
            .thenComparing(RecommendationEngine::compareKeys));
        for (List<String> additions : reviewedAdditions) {
            if (configurationFits.test(additions)) {
                return new RecommendationDecision(RECOMMENDED, additions, List.of(), List.of());
            }
        }
        return new RecommendationDecision(NO_COMPATIBLE_TOOLS, List.of(), List.of(),
            List.of("no_verified_compatible_combination"));
    }

    private static int compareKeys(List<String> left, List<String> right) {
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            int comparison = left.get(index).compareTo(right.get(index));
            if (comparison != 0) return comparison;
        }
        return Integer.compare(left.size(), right.size());
    }

    private boolean supports(String key, EnvironmentTarget target) {
        return release.tools().get(key).support().stream().anyMatch(support -> support.verifiedFor(target));
    }

    private boolean relevantUnknownInstalledVersion(String key, Set<String> required,
                                                     EnvironmentTarget target) {
        CatalogTool tool = release.tools().get(key);
        if (tool == null) return false;
        if (!java.util.Collections.disjoint(tool.capabilityKeys(), required)) return true;
        return release.verifiedCombinations().stream().anyMatch(combination ->
            combination.target().equals(target) && combination.toolKeys().contains(key)
                && !java.util.Collections.disjoint(capabilities(combination.toolKeys()), required));
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
