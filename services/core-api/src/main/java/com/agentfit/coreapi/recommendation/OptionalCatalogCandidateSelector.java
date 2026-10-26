package com.agentfit.coreapi.recommendation;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.CatalogValidator;
import com.agentfit.coreapi.recommendation.selection.ConfigurationSelectionValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Offers one verified, individually selectable addition for each explicit optional need. */
public final class OptionalCatalogCandidateSelector {
    private OptionalCatalogCandidateSelector() {}

    public static List<String> select(CatalogRelease release, EnvironmentTarget target,
                                      Map<String, String> installedVersions,
                                      List<String> baseAdditions,
                                      Set<String> optionalCapabilities,
                                      Predicate<List<String>> configurationFits) {
        if (release == null || target == null || !target.complete()
            || installedVersions == null || baseAdditions == null || baseAdditions.isEmpty()
            || optionalCapabilities == null || configurationFits == null
            || !CapabilityKey.keys().containsAll(optionalCapabilities)
            || new HashSet<>(baseAdditions).size() != baseAdditions.size()
            || !release.tools().keySet().containsAll(baseAdditions)) {
            throw new IllegalArgumentException("invalid optional recommendation context");
        }
        CatalogValidator.validate(release);
        ConfigurationSelectionValidator validator = new ConfigurationSelectionValidator(release);
        Set<String> effectiveBase = new HashSet<>(baseAdditions);
        for (var entry : installedVersions.entrySet()) {
            CatalogTool tool = release.tools().get(entry.getKey());
            if (tool != null && tool.version().equals(entry.getValue())
                && tool.support().stream().anyMatch(row -> row.verifiedFor(target))) {
                effectiveBase.add(tool.key());
            }
        }
        Set<String> covered = new HashSet<>();
        for (String key : effectiveBase) covered.addAll(release.tools().get(key).capabilityKeys());

        List<String> offers = new ArrayList<>();
        for (String capability : optionalCapabilities.stream().sorted().toList()) {
            if (covered.contains(capability)) continue;
            for (CatalogTool candidate : release.tools().values().stream()
                .sorted(java.util.Comparator.comparing(CatalogTool::key)).toList()) {
                if (effectiveBase.contains(candidate.key()) || offers.contains(candidate.key())
                    || !candidate.capabilityKeys().contains(capability)) continue;
                Set<String> together = new HashSet<>(baseAdditions);
                together.add(candidate.key());
                try {
                    validator.validate(together, target, List.of(), List.of(), installedVersions);
                } catch (ConfigurationSelectionValidator.InvalidSelectionException invalid) {
                    continue;
                }
                List<String> renderedKeys = together.stream().sorted().toList();
                if (!configurationFits.test(renderedKeys)) continue;
                offers.add(candidate.key());
                covered.addAll(candidate.capabilityKeys());
                break;
            }
        }
        return List.copyOf(offers);
    }
}
