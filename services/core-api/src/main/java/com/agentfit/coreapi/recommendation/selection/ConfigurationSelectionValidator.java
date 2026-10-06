package com.agentfit.coreapi.recommendation.selection;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.CatalogValidator;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Rechecks a complete tool/policy choice before a future Preview is generated. */
public final class ConfigurationSelectionValidator {
    private final CatalogRelease release;

    public ConfigurationSelectionValidator(CatalogRelease release) {
        CatalogValidator.validate(release);
        this.release = release;
    }

    public List<PermissionSelection> validate(Set<String> selectedToolKeys,
                                              EnvironmentTarget target,
                                              List<PermissionMapping> reviewedMappings,
                                              List<PermissionSelection> selections) {
        if (selectedToolKeys == null || target == null || !target.complete()
            || reviewedMappings == null || selections == null
            || !release.tools().keySet().containsAll(selectedToolKeys)) {
            throw new InvalidSelectionException("unknown tool or incomplete target/selection");
        }
        Set<String> components = new HashSet<>();
        for (String key : selectedToolKeys) {
            CatalogTool tool = release.tools().get(key);
            if (tool.support().stream().noneMatch(support -> support.verifiedFor(target))
                || !selectedToolKeys.containsAll(tool.dependencyKeys())) {
                throw new InvalidSelectionException("unsupported tool or missing dependency");
            }
            for (String conflict : tool.conflictKeys()) {
                if (selectedToolKeys.contains(conflict)) {
                    throw new InvalidSelectionException("conflicting tools");
                }
            }
            for (String component : tool.includedComponentKeys()) {
                if (!components.add(component)) {
                    throw new InvalidSelectionException("overlapping component");
                }
            }
        }
        if (selectedToolKeys.size() > 1 && !release.verifiedCombinations()
            .contains(new VerifiedCombination(selectedToolKeys, target))) {
            throw new InvalidSelectionException("unverified combination for target");
        }

        List<PermissionMapping> applicable = new ArrayList<>();
        for (PermissionMapping mapping : reviewedMappings) {
            if (mapping == null || mapping.toolKey() == null
                || !release.tools().containsKey(mapping.toolKey())) {
                throw new InvalidSelectionException("invalid reviewed mapping");
            }
            if (selectedToolKeys.contains(mapping.toolKey())) applicable.add(mapping);
        }
        try {
            return PermissionPolicyGate.validate(applicable, selections);
        } catch (PermissionPolicyGate.BlockedSelectionException exception) {
            throw new InvalidSelectionException(exception.getMessage(), exception);
        }
    }

    public static final class InvalidSelectionException extends IllegalArgumentException {
        public InvalidSelectionException(String message) { super(message); }
        public InvalidSelectionException(String message, Throwable cause) { super(message, cause); }
    }
}
