package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogStaticTemplateRendererTest {
    @TempDir Path directory;

    @Test
    void copiesOnlySelectedCatalogSourcesAfterIndependentHashApproval() throws IOException {
        Map<String, String> files = files();
        files.put("templates/second.txt", "second\n");
        files.put("templates/index.json", index(row("example-tool", "main", "config/main.txt",
            "templates/main.txt") + "," + row("second-tool", "second", "config/second.txt",
            "templates/second.txt")));
        String approvedHash = writeBundle(files);

        assertEquals(List.of(new PreviewInputFile("main", "config/main.txt", "literal config\n")),
            CatalogStaticTemplateRenderer.render(directory, approvedHash, Set.of("example-tool")));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, "0".repeat(64), Set.of("example-tool")));
        Files.writeString(directory.resolve("templates/main.txt"), "tampered");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, approvedHash, Set.of("example-tool")));
    }

    @Test
    void rejectsUnknownToolsOrOrphanSources() throws IOException {
        Map<String, String> files = files();
        files.put("templates/index.json", index(row("missing-tool", "main", "main.txt",
            "templates/main.txt")));
        String unknownToolHash = writeBundle(files);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, unknownToolHash, Set.of("example-tool")));

        files.put("templates/index.json", index(row("example-tool", "main", "main.txt",
            "templates/main.txt")));
        files.put("templates/orphan.txt", "not indexed");
        String currentHash = writeBundle(files);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, currentHash, Set.of("example-tool")));
    }

    @Test
    void rejectsConflictingOrUnsafeOutputPathsAndUnknownSelection() throws IOException {
        Map<String, String> files = files();
        files.put("templates/index.json", index(row("example-tool", "main", "../escape.txt",
            "templates/main.txt")));
        String unsafePathHash = writeBundle(files);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, unsafePathHash, Set.of("example-tool")));

        files.put("templates/index.json", index(row("example-tool", "main", "main.txt",
            "templates/main.txt")));
        String currentHash = writeBundle(files);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, currentHash, Set.of("absent")));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, currentHash, Set.of()));
    }

    @Test
    void rejectsDuplicateOutputEvenWhenOtherToolIsNotSelected() throws IOException {
        Map<String, String> files = files();
        files.put("templates/second.txt", "second\n");
        files.put("templates/index.json", index(row("example-tool", "main", "config/main.txt",
            "templates/main.txt") + "," + row("second-tool", "different", "config/main.txt",
            "templates/second.txt")));
        String hash = writeBundle(files);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, hash, Set.of("example-tool")));
    }

    @Test
    void rejectsSelectedToolWithNoReviewedOutput() throws IOException {
        Map<String, String> files = files();
        files.put("templates/index.json", index(row("example-tool", "main", "config/main.txt",
            "templates/main.txt")));
        String hash = writeBundle(files);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, hash,
                Set.of("example-tool", "second-tool")));
    }

    @Test
    void rejectsUnknownIndexFieldsAndMissingIndex() throws IOException {
        Map<String, String> files = files();
        String hash = writeBundle(files);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, hash, Set.of("example-tool")));

        files.put("templates/index.json", index(row("example-tool", "main", "main.txt",
            "templates/main.txt")).replace("\"items\":", "\"unexpected\":true,\"items\":"));
        String malformedHash = writeBundle(files);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, malformedHash, Set.of("example-tool")));
    }

    @Test
    void rejectsTemplateIndexSchemaVersionThatTruncatesToOne() throws IOException {
        Map<String, String> files = files();
        files.put("templates/index.json", index(row("example-tool", "main", "main.txt",
            "templates/main.txt")).replace("\"schemaVersion\":1",
                "\"schemaVersion\":4294967297"));
        String hash = writeBundle(files);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogStaticTemplateRenderer.render(directory, hash, Set.of("example-tool")));
    }

    private static String row(String tool, String target, String output, String source) {
        return "{\"toolKey\":\"" + tool + "\",\"targetKey\":\"" + target
            + "\",\"relativePath\":\"" + output + "\",\"sourcePath\":\"" + source + "\"}";
    }

    private static String index(String rows) {
        return "{\"schemaVersion\":1,\"items\":[" + rows + "]}";
    }

    private static Map<String, String> files() {
        Map<String, String> files = new HashMap<>();
        files.put("capabilities.json", "{\"schemaVersion\":1,\"items\":["
            + "{\"key\":\"cap_document_reference\"},{\"key\":\"cap_repository_navigation\"},"
            + "{\"key\":\"cap_code_review\"},{\"key\":\"cap_test_authoring\"},"
            + "{\"key\":\"cap_test_execution\"},{\"key\":\"cap_browser_verification\"},"
            + "{\"key\":\"cap_api_verification\"},{\"key\":\"cap_database_schema_inspection\"},"
            + "{\"key\":\"cap_ai_evaluation\"}]}");
        files.put("tools.json", "{\"schemaVersion\":1,\"items\":["
            + "{\"key\":\"example-tool\",\"version\":\"1.0\",\"capabilityKeys\":[\"cap_document_reference\"],\"includedComponentKeys\":[\"example\"]},"
            + "{\"key\":\"second-tool\",\"version\":\"1.0\",\"capabilityKeys\":[\"cap_test_execution\"],\"includedComponentKeys\":[\"second\"]}]}");
        files.put("support-matrix.json", "{\"schemaVersion\":1,\"items\":[]}");
        files.put("relations.json", "{\"schemaVersion\":1,\"dependencies\":[],\"conflicts\":[],\"verifiedCombinations\":[]}");
        files.put("permissions.json", "{\"schemaVersion\":1,\"items\":[]}");
        files.put("client-capabilities.json", "{\"schemaVersion\":1,\"items\":[]}");
        files.put("templates/main.txt", "literal config\n");
        return files;
    }

    private String writeBundle(Map<String, String> files) throws IOException {
        StringBuilder preimage = new StringBuilder("agentfit-catalog-v1\nsynthetic-templates\n");
        StringBuilder entries = new StringBuilder();
        for (String name : files.keySet().stream().sorted().toList()) {
            Path file = directory.resolve(name);
            Files.createDirectories(file.getParent());
            Files.writeString(file, files.get(name));
            String hash = sha256(files.get(name));
            preimage.append(name).append('\t').append(hash).append('\n');
            if (!entries.isEmpty()) entries.append(',');
            entries.append("{\"path\":\"").append(name).append("\",\"sha256\":\"")
                .append(hash).append("\"}");
        }
        String catalogHash = sha256(preimage.toString());
        Files.writeString(directory.resolve("manifest.json"), "{\"schemaVersion\":1,"
            + "\"releaseId\":\"synthetic-templates\",\"catalogHash\":\"" + catalogHash
            + "\",\"files\":[" + entries + "]}");
        return catalogHash;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
