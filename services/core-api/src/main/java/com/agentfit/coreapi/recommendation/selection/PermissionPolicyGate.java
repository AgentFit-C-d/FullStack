package com.agentfit.coreapi.recommendation.selection;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PermissionPolicyGate {
    private PermissionPolicyGate() {}

    public static List<PermissionSelection> defaults(List<PermissionMapping> mappings) {
        if (mappings == null) throw new BlockedSelectionException("missing reviewed mappings");
        List<PermissionSelection> result = new ArrayList<>();
        Set<Key> seen = new HashSet<>();
        for (PermissionMapping mapping : mappings) {
            if (!valid(mapping)) {
                throw new BlockedSelectionException("invalid reviewed mapping");
            }
            Key key = new Key(mapping.toolKey(), mapping.mappingKey());
            if (!seen.add(key) || !mapping.supportedPolicies().contains(PermissionPolicy.ASK_EACH_TIME)) {
                throw new BlockedSelectionException("missing unique verified Ask mapping");
            }
            result.add(new PermissionSelection(mapping.toolKey(), mapping.mappingKey(),
                PermissionPolicy.ASK_EACH_TIME));
        }
        return List.copyOf(result);
    }

    public static List<PermissionSelection> validate(List<PermissionMapping> mappings,
                                                      List<PermissionSelection> selections) {
        if (mappings == null || selections == null) {
            throw new BlockedSelectionException("missing mapping selection");
        }
        Map<Key, PermissionMapping> reviewed = new HashMap<>();
        for (PermissionMapping mapping : mappings) {
            if (!valid(mapping)
                || !mapping.supportedPolicies().contains(PermissionPolicy.ASK_EACH_TIME)
                || reviewed.putIfAbsent(new Key(mapping.toolKey(), mapping.mappingKey()), mapping) != null) {
                throw new BlockedSelectionException("invalid or duplicate reviewed mapping");
            }
        }
        Set<Key> selected = new HashSet<>();
        for (PermissionSelection selection : selections) {
            if (selection == null || selection.policy() == null) {
                throw new BlockedSelectionException("missing policy");
            }
            Key key = new Key(selection.toolKey(), selection.mappingKey());
            PermissionMapping mapping = reviewed.get(key);
            if (mapping == null || !selected.add(key)) {
                throw new BlockedSelectionException("unknown or duplicate mapping");
            }
            if (!mapping.supportedPolicies().contains(selection.policy())
                || (mapping.required() && selection.policy() == PermissionPolicy.DENY)) {
                throw new BlockedSelectionException("unsupported or denied required policy");
            }
        }
        if (!selected.equals(reviewed.keySet())) {
            throw new BlockedSelectionException("missing mapping selection");
        }
        return List.copyOf(selections);
    }

    private static boolean valid(PermissionMapping mapping) {
        return mapping != null && mapping.toolKey() != null && !mapping.toolKey().isBlank()
            && mapping.mappingKey() != null && !mapping.mappingKey().isBlank()
            && mapping.supportedPolicies() != null && !mapping.supportedPolicies().isEmpty();
    }

    public static final class BlockedSelectionException extends IllegalArgumentException {
        public BlockedSelectionException(String message) { super(message); }
    }

    private record Key(String toolKey, String mappingKey) {}
}
