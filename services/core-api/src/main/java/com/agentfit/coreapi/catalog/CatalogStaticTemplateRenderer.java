package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewFileComparator;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Copies static Catalog text into Preview candidates only after an external release hash pin matches. */
public final class CatalogStaticTemplateRenderer {
    private static final ObjectMapper JSON = new ObjectMapper(
        new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    private static final String INDEX = "templates/index.json";
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "items");
    private static final Set<String> ITEM_FIELDS = Set.of("toolKey", "targetKey", "relativePath", "sourcePath");

    private CatalogStaticTemplateRenderer() {}

    public static List<PreviewInputFile> render(Path releaseDirectory, String approvedCatalogHash,
                                                 Set<String> selectedToolKeys) {
        return render(CatalogBundleLoader.load(releaseDirectory), approvedCatalogHash, selectedToolKeys);
    }

    static List<PreviewInputFile> render(VerifiedCatalogBundle bundle, String approvedCatalogHash,
                                         Set<String> selectedToolKeys) {
        if (bundle == null || approvedCatalogHash == null
            || !approvedCatalogHash.matches("[0-9a-f]{64}")
            || !MessageDigest.isEqual(bundle.catalogHash().getBytes(StandardCharsets.US_ASCII),
                approvedCatalogHash.getBytes(StandardCharsets.US_ASCII))) {
            throw unavailable("catalog hash is not approved");
        }
        ParsedCatalog parsed = CatalogSemanticParser.parse(bundle);
        if (selectedToolKeys == null || selectedToolKeys.isEmpty()
            || selectedToolKeys.stream().anyMatch(Objects::isNull)
            || !parsed.release().tools().keySet().containsAll(selectedToolKeys)) {
            throw unavailable("invalid selected tool keys");
        }
        try {
            String indexText = bundle.files().get(INDEX);
            if (indexText == null) throw unavailable("template index missing");
            JsonNode root = JSON.readTree(indexText);
            exact(root, ROOT_FIELDS);
            if (!root.path("schemaVersion").isIntegralNumber()
                || root.path("schemaVersion").intValue() != 1
                || !root.path("items").isArray()) throw unavailable("invalid template index schema");

            Set<String> sourcePaths = new HashSet<>();
            Set<String> coveredSelectedTools = new HashSet<>();
            List<PreviewInputFile> all = new ArrayList<>();
            List<PreviewInputFile> selected = new ArrayList<>();
            for (JsonNode item : root.path("items")) {
                exact(item, ITEM_FIELDS);
                String toolKey = value(item, "toolKey");
                String sourcePath = value(item, "sourcePath");
                if (!parsed.release().tools().containsKey(toolKey)
                    || !(sourcePath.startsWith("templates/") || sourcePath.startsWith("guides/"))
                    || sourcePath.equals(INDEX) || !sourcePaths.add(sourcePath)) {
                    throw unavailable("invalid template source or tool");
                }
                String content = bundle.files().get(sourcePath);
                if (content == null) throw unavailable("unlisted template source");
                PreviewInputFile file = new PreviewInputFile(value(item, "targetKey"),
                    value(item, "relativePath"), content);
                all.add(file);
                if (selectedToolKeys.contains(toolKey)) {
                    coveredSelectedTools.add(toolKey);
                    selected.add(file);
                }
            }
            Set<String> listedSources = new HashSet<>();
            for (String path : bundle.files().keySet()) {
                if ((path.startsWith("templates/") || path.startsWith("guides/"))
                    && !path.equals(INDEX)) listedSources.add(path);
            }
            if (!listedSources.equals(sourcePaths) || !coveredSelectedTools.equals(selectedToolKeys)) {
                throw unavailable("orphan source or selected tool without template");
            }
            // Reuse the Preview boundary to reject traversal, device names, duplicate targets,
            // and ambiguous output paths before any candidate can enter Preview.
            PreviewFileComparator.compare(all, ExistingState.UNKNOWN, List.of());
            return List.copyOf(selected);
        } catch (CatalogBundleLoader.CatalogUnavailableException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new CatalogBundleLoader.CatalogUnavailableException("invalid static template index", exception);
        }
    }

    private static void exact(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) throw unavailable("expected template object");
        Set<String> actual = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw unavailable("unexpected template field");
    }

    private static String value(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()
            || !value.textValue().equals(value.textValue().trim())) {
            throw unavailable("invalid template field: " + field);
        }
        return value.textValue();
    }

    private static CatalogBundleLoader.CatalogUnavailableException unavailable(String reason) {
        return new CatalogBundleLoader.CatalogUnavailableException(reason);
    }
}
