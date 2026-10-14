package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.selection.ConfigurationSelectionValidator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reports required capabilities left unmet by a valid partial Preview choice. */
public final class PreviewCapabilityCoverage {
    private PreviewCapabilityCoverage() {}

    public record Result(List<String> unmetCapabilityKeys) {
        public Result {
            unmetCapabilityKeys = List.copyOf(unmetCapabilityKeys);
        }

        public boolean complete() { return unmetCapabilityKeys.isEmpty(); }
    }

    public static Result evaluate(CatalogRelease release, EnvironmentTarget target,
                                  Set<String> requiredCapabilityKeys,
                                  List<String> selectedToolKeys,
                                  Map<String, String> trustedInstalledVersions) {
        if (release == null || target == null || !target.complete()
            || requiredCapabilityKeys == null || selectedToolKeys == null
            || trustedInstalledVersions == null
            || !CapabilityKey.keys().containsAll(requiredCapabilityKeys)) {
            throw new IllegalArgumentException("invalid Preview capability context");
        }
        Set<String> effective = new HashSet<>();
        for (String key : selectedToolKeys) {
            CatalogTool tool = key == null ? null : release.tools().get(key);
            if (tool == null || !effective.add(key)
                || tool.support().stream().noneMatch(row -> row.verifiedFor(target))) {
                throw new IllegalArgumentException("invalid selected Preview tool");
            }
        }
        for (var entry : trustedInstalledVersions.entrySet()) {
            CatalogTool tool = release.tools().get(entry.getKey());
            if (tool != null && tool.version().equals(entry.getValue())
                && tool.support().stream().anyMatch(row -> row.verifiedFor(target))) {
                effective.add(tool.key());
            }
        }
        // Coverage may only credit an effective set that Preview itself would accept.
        // A supported installed tool alone does not prove its dependencies or combination.
        new ConfigurationSelectionValidator(release).validate(Set.copyOf(selectedToolKeys),
            target, List.of(), List.of(), trustedInstalledVersions);
        Set<String> covered = new HashSet<>();
        for (String key : effective) covered.addAll(release.tools().get(key).capabilityKeys());
        return new Result(requiredCapabilityKeys.stream().filter(key -> !covered.contains(key))
            .sorted().toList());
    }
}
