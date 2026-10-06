package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.CatalogValidator;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.selection.PermissionMapping;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict proposed v1 schema. All references and review sources are checked before use. */
public final class CatalogSemanticParser {
    private static final ObjectMapper JSON = new ObjectMapper(
        new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> ROOT_ITEMS = Set.of("schemaVersion", "items");
    private static final Set<String> REQUIRED_FILES = Set.of("capabilities.json", "tools.json",
        "support-matrix.json", "relations.json", "permissions.json", "client-capabilities.json");

    private CatalogSemanticParser() {}

    public static ParsedCatalog load(Path releaseDirectory) {
        return parse(CatalogBundleLoader.load(releaseDirectory));
    }

    static ParsedCatalog parse(VerifiedCatalogBundle bundle) {
        if (bundle == null || bundle.releaseId() == null || bundle.releaseId().isBlank()
            || bundle.catalogHash() == null || bundle.catalogHash().isBlank()
            || bundle.files() == null || !bundle.files().keySet().containsAll(REQUIRED_FILES)) {
            throw unavailable("incomplete verified bundle");
        }
        try {
            Set<String> capabilities = new HashSet<>();
            for (JsonNode item : items(bundle, "capabilities.json")) {
                exact(item, Set.of("key"));
                if (!capabilities.add(value(item, "key"))) throw unavailable("duplicate capability");
            }
            if (!capabilities.equals(CapabilityKey.keys())) throw unavailable("capability set mismatch");

            Map<String, DraftTool> drafts = new HashMap<>();
            for (JsonNode item : items(bundle, "tools.json")) {
                exact(item, Set.of("key", "version", "capabilityKeys", "includedComponentKeys"));
                String key = value(item, "key");
                Set<String> toolCapabilities = uniqueStrings(item, "capabilityKeys");
                if (!capabilities.containsAll(toolCapabilities)) {
                    throw unavailable("invalid tool capabilities");
                }
                DraftTool draft = new DraftTool(value(item, "version"), toolCapabilities,
                    uniqueStrings(item, "includedComponentKeys"));
                if (drafts.putIfAbsent(key, draft) != null) throw unavailable("duplicate tool");
            }

            Map<String, Set<String>> dependencies = new HashMap<>();
            Map<String, Set<String>> conflicts = new HashMap<>();
            JsonNode relations = root(bundle, "relations.json", Set.of("schemaVersion", "dependencies",
                "conflicts", "verifiedCombinations"));
            readRelations(relations, "dependencies", drafts.keySet(), dependencies);
            readRelations(relations, "conflicts", drafts.keySet(), conflicts);

            Map<String, VerificationEvidence> evidence = new HashMap<>();
            Set<VerifiedCombination> combinations = new HashSet<>();
            for (JsonNode item : array(relations, "verifiedCombinations")) {
                exact(item, Set.of("toolKeys", "osFamily", "clientId", "clientVersion",
                    "evidenceUrl", "checkedAt"));
                Set<String> toolKeys = uniqueStrings(item, "toolKeys");
                EnvironmentTarget target = new EnvironmentTarget(value(item, "osFamily"),
                    value(item, "clientId"), value(item, "clientVersion"));
                if (toolKeys.size() < 2 || !drafts.keySet().containsAll(toolKeys)
                    || !combinations.add(new VerifiedCombination(toolKeys, target))) {
                    throw unavailable("invalid verified combination");
                }
                String evidenceKey = "combination:" + target.osFamily() + ":" + target.clientId()
                    + ":" + target.clientVersion() + ":"
                    + String.join(",", toolKeys.stream().sorted().toList());
                putEvidence(evidence, evidenceKey, evidence(item));
            }

            Map<String, List<ToolSupport>> support = new HashMap<>();
            Set<String> supportKeys = new HashSet<>();
            for (JsonNode item : items(bundle, "support-matrix.json")) {
                exact(item, Set.of("key", "toolKey", "osFamily", "clientId", "clientVersion",
                    "documentation", "format", "standalone", "evidenceUrl", "checkedAt"));
                String key = value(item, "key");
                String toolKey = value(item, "toolKey");
                if (!drafts.containsKey(toolKey) || !supportKeys.add(key)) {
                    throw unavailable("invalid support reference");
                }
                VerificationEvidence source = evidence(item);
                ToolSupport row = new ToolSupport(key, value(item, "osFamily"), value(item, "clientId"),
                    value(item, "clientVersion"), check(item, "documentation"), check(item, "format"),
                    check(item, "standalone"));
                support.computeIfAbsent(toolKey, ignored -> new ArrayList<>()).add(row);
                putEvidence(evidence, "support:" + key, source);
            }

            List<PermissionMapping> mappings = new ArrayList<>();
            Set<String> mappingKeys = new HashSet<>();
            for (JsonNode item : items(bundle, "permissions.json")) {
                exact(item, Set.of("toolKey", "mappingKey", "required", "supportedPolicies",
                    "evidenceUrl", "checkedAt"));
                String toolKey = value(item, "toolKey");
                String mappingKey = value(item, "mappingKey");
                if (!drafts.containsKey(toolKey) || !mappingKeys.add(toolKey + "\u0000" + mappingKey)
                    || !item.path("required").isBoolean()) throw unavailable("invalid permission mapping");
                Set<PermissionPolicy> policies = new HashSet<>();
                for (String policy : uniqueStrings(item, "supportedPolicies")) {
                    try { policies.add(PermissionPolicy.valueOf(policy)); }
                    catch (IllegalArgumentException exception) { throw unavailable("unknown permission policy"); }
                }
                if (!policies.contains(PermissionPolicy.ASK_EACH_TIME)) {
                    throw unavailable("permission mapping lacks Ask");
                }
                mappings.add(new PermissionMapping(toolKey, mappingKey,
                    item.path("required").booleanValue(), policies));
                putEvidence(evidence, "permission:" + toolKey + ":" + mappingKey,
                    evidence(item));
            }

            if (items(bundle, "client-capabilities.json").size() != 0) {
                throw unavailable("client capability schema not yet agreed");
            }

            Map<String, CatalogTool> tools = new HashMap<>();
            for (Map.Entry<String, DraftTool> entry : drafts.entrySet()) {
                String key = entry.getKey();
                DraftTool draft = entry.getValue();
                tools.put(key, new CatalogTool(key, draft.version(), draft.capabilities(),
                    dependencies.getOrDefault(key, Set.of()), conflicts.getOrDefault(key, Set.of()),
                    draft.components(), support.getOrDefault(key, List.of())));
            }
            CatalogRelease release = new CatalogRelease(bundle.releaseId(), tools, combinations);
            CatalogValidator.validate(release);
            return new ParsedCatalog(bundle.catalogHash(), release, mappings, evidence);
        } catch (CatalogBundleLoader.CatalogUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CatalogBundleLoader.CatalogUnavailableException("invalid catalog semantics", exception);
        }
    }

    private static void readRelations(JsonNode root, String field, Set<String> toolKeys,
                                      Map<String, Set<String>> target) {
        for (JsonNode item : array(root, field)) {
            exact(item, Set.of("toolKey", "targetKey"));
            String from = value(item, "toolKey");
            String to = value(item, "targetKey");
            if (!toolKeys.contains(from) || !toolKeys.contains(to) || from.equals(to)
                || !target.computeIfAbsent(from, ignored -> new HashSet<>()).add(to)) {
                throw unavailable("invalid relation");
            }
        }
    }

    private static JsonNode root(VerifiedCatalogBundle bundle, String name, Set<String> expected) {
        try {
            JsonNode root = JSON.readTree(bundle.files().get(name));
            exact(root, expected);
            JsonNode version = root.path("schemaVersion");
            if (!version.isInt() || version.intValue() != 1) throw unavailable("unsupported schema");
            return root;
        } catch (java.io.IOException exception) {
            throw new CatalogBundleLoader.CatalogUnavailableException("invalid catalog JSON", exception);
        }
    }

    private static JsonNode items(VerifiedCatalogBundle bundle, String name) {
        return array(root(bundle, name, ROOT_ITEMS), "items");
    }

    private static JsonNode array(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isArray()) throw unavailable("expected array: " + field);
        return value;
    }

    private static void exact(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) throw unavailable("expected object");
        Set<String> fields = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(fields::add);
        if (!fields.equals(expected)) throw unavailable("unexpected or missing field");
    }

    private static String value(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isTextual() || node.textValue().isBlank() || !node.textValue().equals(node.textValue().trim())) {
            throw unavailable("invalid field: " + field);
        }
        return node.textValue();
    }

    private static Set<String> uniqueStrings(JsonNode parent, String field) {
        Set<String> values = new HashSet<>();
        for (JsonNode item : array(parent, field)) {
            if (!item.isTextual() || item.textValue().isBlank()
                || !item.textValue().equals(item.textValue().trim()) || !values.add(item.textValue())) {
                throw unavailable("invalid or duplicate: " + field);
            }
        }
        return Set.copyOf(values);
    }

    private static ToolSupport.Check check(JsonNode parent, String field) {
        try { return ToolSupport.Check.valueOf(value(parent, field)); }
        catch (IllegalArgumentException exception) { throw unavailable("invalid support check"); }
    }

    private static VerificationEvidence evidence(JsonNode item) {
        String url = value(item, "evidenceUrl");
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || uri.getUserInfo() != null || uri.getFragment() != null) throw unavailable("invalid evidence URL");
        try { return new VerificationEvidence(url, LocalDate.parse(value(item, "checkedAt"))); }
        catch (DateTimeParseException exception) { throw unavailable("invalid verification date"); }
    }

    private static void putEvidence(Map<String, VerificationEvidence> evidence, String key,
                                    VerificationEvidence source) {
        if (evidence.putIfAbsent(key, source) != null) {
            throw unavailable("ambiguous verification evidence key");
        }
    }

    private static CatalogBundleLoader.CatalogUnavailableException unavailable(String reason) {
        return new CatalogBundleLoader.CatalogUnavailableException(reason);
    }

    private record DraftTool(String version, Set<String> capabilities, Set<String> components) {}
}
