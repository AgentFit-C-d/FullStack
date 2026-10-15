package com.agentfit.coreapi.catalog.model;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Invalid release data is an operational error, never a normal no-candidates result. */
public final class CatalogValidator {
    private CatalogValidator() {}

    public static void validate(CatalogRelease release) {
        if (release.releaseId() == null || release.releaseId().isBlank()) {
            throw new InvalidCatalogException("missing release ID");
        }
        Set<String> supportKeys = new HashSet<>();
        for (Map.Entry<String, CatalogTool> entry : release.tools().entrySet()) {
            CatalogTool tool = entry.getValue();
            if (!entry.getKey().equals(tool.key()) || blank(tool.version())) {
                throw new InvalidCatalogException("tool ID/version mismatch: " + entry.getKey());
            }
            if (blank(tool.name()) || !tool.name().equals(tool.name().trim())
                || tool.name().codePoints().anyMatch(Character::isISOControl)
                || tool.kind() == null || !validSource(tool.sourceUrl())) {
                throw new InvalidCatalogException("tool identity/source missing: " + tool.key());
            }
            if (!CapabilityKey.keys().containsAll(tool.capabilityKeys())) {
                throw new InvalidCatalogException("unknown capability: " + tool.key());
            }
            if (tool.dependencyKeys().contains(tool.key()) || tool.conflictKeys().contains(tool.key())) {
                throw new InvalidCatalogException("self relation: " + tool.key());
            }
            if (!release.tools().keySet().containsAll(tool.dependencyKeys())
                || !release.tools().keySet().containsAll(tool.conflictKeys())) {
                throw new InvalidCatalogException("missing relation target: " + tool.key());
            }
            Set<EnvironmentTarget> supportTargets = new HashSet<>();
            for (ToolSupport support : tool.support()) {
                if (blank(support.key()) || blank(support.osFamily()) || blank(support.clientId())
                    || blank(support.clientVersion()) || support.documentation() == null
                    || support.format() == null || support.standalone() == null
                    || !supportKeys.add(support.key())
                    || !supportTargets.add(new EnvironmentTarget(support.osFamily(),
                        support.clientId(), support.clientVersion()))) {
                    throw new InvalidCatalogException("invalid/duplicate support: " + tool.key());
                }
            }
        }
        Set<String> visited = new HashSet<>();
        for (String key : release.tools().keySet()) {
            visit(key, release.tools(), visited, new HashSet<>());
        }
        for (String key : release.tools().keySet()) {
            Set<String> requiredTogether = new HashSet<>();
            includeDependencies(key, release.tools(), requiredTogether);
            for (String member : requiredTogether) {
                if (!java.util.Collections.disjoint(release.tools().get(member).conflictKeys(),
                    requiredTogether)) {
                    throw new InvalidCatalogException("contradictory dependency/conflict: " + key);
                }
            }
        }
        for (VerifiedCombination combination : release.verifiedCombinations()) {
            if (combination == null || combination.toolKeys().size() < 2
                || combination.target() == null || !combination.target().complete()
                || !release.tools().keySet().containsAll(combination.toolKeys())) {
                throw new InvalidCatalogException("invalid verified combination");
            }
            Set<String> components = new HashSet<>();
            for (String key : combination.toolKeys()) {
                CatalogTool tool = release.tools().get(key);
                if (tool.support().stream().noneMatch(row -> row.verifiedFor(combination.target()))
                    || !combination.toolKeys().containsAll(tool.dependencyKeys())) {
                    throw new InvalidCatalogException("unsupported combination member or dependency");
                }
                for (String conflict : tool.conflictKeys()) {
                    if (combination.toolKeys().contains(conflict)) {
                        throw new InvalidCatalogException("conflicting combination members");
                    }
                }
                for (String component : tool.includedComponentKeys()) {
                    if (!components.add(component)) {
                        throw new InvalidCatalogException("overlapping combination components");
                    }
                }
            }
        }
    }

    private static void visit(String key, Map<String, CatalogTool> tools,
                              Set<String> visited, Set<String> active) {
        if (visited.contains(key)) return;
        if (!active.add(key)) throw new InvalidCatalogException("dependency cycle: " + key);
        for (String dependency : tools.get(key).dependencyKeys()) {
            visit(dependency, tools, visited, active);
        }
        active.remove(key);
        visited.add(key);
    }

    private static void includeDependencies(String key, Map<String, CatalogTool> tools,
                                            Set<String> requiredTogether) {
        if (!requiredTogether.add(key)) return;
        for (String dependency : tools.get(key).dependencyKeys()) {
            includeDependencies(dependency, tools, requiredTogether);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean validSource(String value) {
        if (blank(value)) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static final class InvalidCatalogException extends IllegalArgumentException {
        public InvalidCatalogException(String message) {
            super(message);
        }
    }
}
