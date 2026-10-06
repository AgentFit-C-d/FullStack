package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogSemanticParserTest {
    @TempDir Path directory;
    private static final String CAPABILITIES = """
        {"schemaVersion":1,"items":[
          {"key":"cap_document_reference"},{"key":"cap_repository_navigation"},
          {"key":"cap_code_review"},{"key":"cap_test_authoring"},
          {"key":"cap_test_execution"},{"key":"cap_browser_verification"},
          {"key":"cap_api_verification"},{"key":"cap_database_schema_inspection"},
          {"key":"cap_ai_evaluation"}]}
        """;
    private static final String TOOLS = """
        {"schemaVersion":1,"items":[{"key":"example-tool","version":"1.0",
        "capabilityKeys":["cap_document_reference"],"includedComponentKeys":["example-component"]}]}
        """;
    private static final String SUPPORT = """
        {"schemaVersion":1,"items":[{"key":"support-1","toolKey":"example-tool",
        "osFamily":"WINDOWS","clientId":"example-client","clientVersion":"1.0",
        "documentation":"PASS","format":"PASS","standalone":"PASS",
        "evidenceUrl":"https://example.org/review/1","checkedAt":"2026-10-06"}]}
        """;
    private static final String RELATIONS = """
        {"schemaVersion":1,"dependencies":[],"conflicts":[],"verifiedCombinations":[]}
        """;
    private static final String PERMISSIONS = """
        {"schemaVersion":1,"items":[{"toolKey":"example-tool","mappingKey":"network",
        "required":false,"supportedPolicies":["ASK_EACH_TIME","DENY"],
        "evidenceUrl":"https://example.org/review/permission","checkedAt":"2026-10-06"}]}
        """;

    private Map<String, String> files() {
        return new HashMap<>(Map.of(
            "capabilities.json", CAPABILITIES,
            "tools.json", TOOLS,
            "support-matrix.json", SUPPORT,
            "relations.json", RELATIONS,
            "permissions.json", PERMISSIONS,
            "client-capabilities.json", "{\"schemaVersion\":1,\"items\":[]}"));
    }

    private ParsedCatalog parse(Map<String, String> files) {
        return CatalogSemanticParser.parse(new VerifiedCatalogBundle("synthetic-1", "test-hash", files));
    }

    private Map<String, String> twoToolFiles() {
        Map<String, String> files = files();
        files.put("tools.json", TOOLS.replace("}]}", "},{\"key\":\"second-tool\",\"version\":\"1.0\","
            + "\"capabilityKeys\":[\"cap_test_execution\"],\"includedComponentKeys\":[\"second-component\"]}]}"));
        files.put("support-matrix.json", SUPPORT.replace("}]}",
            "},{\"key\":\"support-2\",\"toolKey\":\"second-tool\",\"osFamily\":\"WINDOWS\","
                + "\"clientId\":\"example-client\",\"clientVersion\":\"1.0\",\"documentation\":\"PASS\","
                + "\"format\":\"PASS\",\"standalone\":\"PASS\","
                + "\"evidenceUrl\":\"https://example.org/review/2\",\"checkedAt\":\"2026-10-06\"}]}"));
        return files;
    }

    private String targetedCombination() {
        return "{\"toolKeys\":[\"example-tool\",\"second-tool\"],\"osFamily\":\"WINDOWS\","
            + "\"clientId\":\"example-client\",\"clientVersion\":\"1.0\","
            + "\"evidenceUrl\":\"https://example.org/review/combination\",\"checkedAt\":\"2026-10-06\"}";
    }

    @Test
    void projectsOnlyExplicitlyVerifiedSupportAndReviewedPermission() {
        ParsedCatalog parsed = parse(files());
        assertEquals("test-hash", parsed.catalogHash());
        assertEquals("synthetic-1", parsed.release().releaseId());
        assertTrue(parsed.release().tools().get("example-tool").support().getFirst()
            .verifiedFor(new EnvironmentTarget("WINDOWS", "example-client", "1.0")));
        assertFalse(parsed.release().tools().get("example-tool").support().getFirst()
            .verifiedFor(new EnvironmentTarget("WINDOWS", "example-client", "2.0")));
        assertEquals(1, parsed.permissionMappings().size());
        assertTrue(parsed.permissionMappings().getFirst().supportedPolicies().contains(PermissionPolicy.ASK_EACH_TIME));
        assertThrows(UnsupportedOperationException.class, () -> parsed.permissionMappings().clear());
    }

    @Test
    void rejectsUnknownCapabilityAndDanglingReferences() {
        Map<String, String> files = files();
        files.put("tools.json", TOOLS.replace("cap_document_reference", "cap_invented"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(files));

        Map<String, String> dangling = files();
        dangling.put("support-matrix.json", SUPPORT.replace("example-tool", "missing-tool"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(dangling));
    }

    @Test
    void rejectsSupportWithoutEvidenceOrCompletedChecks() {
        Map<String, String> files = files();
        files.put("support-matrix.json", SUPPORT.replace("https://example.org/review/1", ""));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(files));

        Map<String, String> unchecked = files();
        unchecked.put("support-matrix.json", SUPPORT.replace("\"documentation\":\"PASS\"", "\"documentation\":\"NOT_RUN\""));
        ParsedCatalog parsed = parse(unchecked);
        assertFalse(parsed.release().tools().get("example-tool").support().getFirst()
            .verifiedFor(new EnvironmentTarget("WINDOWS", "example-client", "1.0")));
    }

    @Test
    void rejectsUnverifiedCombinationAndUnknownClientCapability() {
        Map<String, String> files = files();
        files.put("relations.json", RELATIONS.replace("\"verifiedCombinations\":[]",
            "\"verifiedCombinations\":[{\"toolKeys\":[\"example-tool\",\"missing-tool\"],"
                + "\"evidenceUrl\":\"https://example.org/review/combination\",\"checkedAt\":\"2026-10-06\"}]"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(files));

        Map<String, String> unknownBuiltIn = files();
        unknownBuiltIn.put("client-capabilities.json", "{\"schemaVersion\":1,\"items\":[{\"key\":\"unknown\"}]}");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(unknownBuiltIn));
    }

    @Test
    void rejectsUnreviewedPermissionAndUnexpectedFields() {
        Map<String, String> files = files();
        files.put("permissions.json", PERMISSIONS.replace("ASK_EACH_TIME", "ALWAYS_ALLOW"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(files));

        Map<String, String> unexpected = files();
        unexpected.put("tools.json", TOOLS.replace("\"version\":\"1.0\"", "\"version\":\"1.0\",\"invented\":true"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(unexpected));
    }

    @Test
    void rejectsEmbeddedControlCharactersInScalarAndArrayMetadata() {
        Map<String, String> version = files();
        version.put("tools.json", TOOLS.replace("\"version\":\"1.0\"",
            "\"version\":\"1.0\\nx\""));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(version));

        Map<String, String> component = files();
        component.put("tools.json", TOOLS.replace("example-component", "example\\u007fcomponent"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(component));
    }

    @Test
    void rejectsDuplicateJsonKeys() {
        Map<String, String> files = files();
        files.put("tools.json", TOOLS.replace("\"version\":\"1.0\"",
            "\"version\":\"0.1\",\"version\":\"1.0\""));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(files));
    }

    @Test
    void rejectsSchemaVersionThatTruncatesToOne() {
        Map<String, String> files = files();
        files.put("tools.json", TOOLS.replace("\"schemaVersion\":1",
            "\"schemaVersion\":4294967297"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(files));
    }

    @Test
    void rejectsTrailingJsonAfterCatalogFile() {
        Map<String, String> files = files();
        files.put("tools.json", TOOLS + " {\"extra\":true}");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(files));
    }

    @Test
    void rejectsDistinctPermissionMappingsWithCollidingEvidenceKeys() {
        Map<String, String> files = files();
        files.put("tools.json", TOOLS.replace("}]}",
            "},{\"key\":\"example-tool:part\",\"version\":\"1.0\","
                + "\"capabilityKeys\":[],\"includedComponentKeys\":[\"second-component\"]}]}"));
        files.put("permissions.json", PERMISSIONS
            .replace("\"mappingKey\":\"network\"", "\"mappingKey\":\"part:tail\"")
            .replace("}]}", "},{\"toolKey\":\"example-tool:part\","
                + "\"mappingKey\":\"tail\",\"required\":false,"
                + "\"supportedPolicies\":[\"ASK_EACH_TIME\"],"
                + "\"evidenceUrl\":\"https://example.org/review/other\","
                + "\"checkedAt\":\"2026-10-06\"}]}"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(files));
    }

    @Test
    void allowsDependencyOnlyToolWithNoDirectCapability() {
        Map<String, String> files = files();
        files.put("tools.json", TOOLS.replace("}]}", "},{\"key\":\"runtime\",\"version\":\"1.0\","
            + "\"capabilityKeys\":[],\"includedComponentKeys\":[\"runtime-component\"]}]}"));
        files.put("relations.json", RELATIONS.replace("\"dependencies\":[]",
            "\"dependencies\":[{\"toolKey\":\"example-tool\",\"targetKey\":\"runtime\"}]"));
        assertEquals(Set.of("runtime"), parse(files).release().tools().get("example-tool").dependencyKeys());
    }

    @Test
    void doesNotActivateEnvironmentAgnosticCombinations() {
        Map<String, String> files = files();
        files.put("tools.json", TOOLS.replace("}]}", "},{\"key\":\"second-tool\",\"version\":\"1.0\","
            + "\"capabilityKeys\":[\"cap_test_execution\"],\"includedComponentKeys\":[\"second-component\"]}]}"));
        files.put("relations.json", RELATIONS.replace("\"verifiedCombinations\":[]",
            "\"verifiedCombinations\":[{\"toolKeys\":[\"example-tool\",\"second-tool\"],"
                + "\"evidenceUrl\":\"https://example.org/review/combination\",\"checkedAt\":\"2026-10-06\"}]"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(files));
    }

    @Test
    void acceptsTargetedCombinationAndRetainsItsEvidence() {
        Map<String, String> files = twoToolFiles();
        files.put("relations.json", RELATIONS.replace("\"verifiedCombinations\":[]",
            "\"verifiedCombinations\":[" + targetedCombination() + "]"));
        ParsedCatalog catalog = parse(files);
        VerifiedCombination combination = new VerifiedCombination(Set.of("example-tool", "second-tool"),
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"));
        assertTrue(catalog.release().verifiedCombinations().contains(combination));
        assertEquals("https://example.org/review/combination",
            catalog.evidence().get("combination:WINDOWS:example-client:1.0:example-tool,second-tool").sourceUrl());
    }

    @Test
    void rejectsDuplicateTargetedCombinationAndUnverifiedMember() {
        Map<String, String> duplicate = twoToolFiles();
        duplicate.put("relations.json", RELATIONS.replace("\"verifiedCombinations\":[]",
            "\"verifiedCombinations\":[" + targetedCombination() + "," + targetedCombination() + "]"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(duplicate));

        Map<String, String> unchecked = twoToolFiles();
        unchecked.put("support-matrix.json", unchecked.get("support-matrix.json")
            .replace("\"standalone\":\"PASS\",\"evidenceUrl\":\"https://example.org/review/2\"",
                "\"standalone\":\"NOT_RUN\",\"evidenceUrl\":\"https://example.org/review/2\""));
        unchecked.put("relations.json", RELATIONS.replace("\"verifiedCombinations\":[]",
            "\"verifiedCombinations\":[" + targetedCombination() + "]"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(unchecked));
    }

    @Test
    void rejectsCombinationWithoutEvidenceAndAmbiguousSupportTarget() {
        Map<String, String> noEvidence = twoToolFiles();
        noEvidence.put("relations.json", RELATIONS.replace("\"verifiedCombinations\":[]",
            "\"verifiedCombinations\":[" + targetedCombination()
                .replace("https://example.org/review/combination", "") + "]"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(noEvidence));

        Map<String, String> ambiguous = twoToolFiles();
        String duplicateTarget = "{\"key\":\"support-3\",\"toolKey\":\"second-tool\","
            + "\"osFamily\":\"WINDOWS\",\"clientId\":\"example-client\",\"clientVersion\":\"1.0\","
            + "\"documentation\":\"FAIL\",\"format\":\"FAIL\",\"standalone\":\"FAIL\","
            + "\"evidenceUrl\":\"https://example.org/review/3\",\"checkedAt\":\"2026-10-06\"}";
        ambiguous.put("support-matrix.json", ambiguous.get("support-matrix.json")
            .replace("}]}", "}," + duplicateTarget + "]}"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class, () -> parse(ambiguous));
    }

    @Test
    void loadsSemanticCatalogOnlyThroughVerifiedFiles() throws IOException {
        Map<String, String> files = files();
        StringBuilder preimage = new StringBuilder("agentfit-catalog-v1\nsynthetic-1\n");
        StringBuilder entries = new StringBuilder();
        for (String name : files.keySet().stream().sorted().toList()) {
            String content = files.get(name);
            Files.writeString(directory.resolve(name), content);
            String hash = sha256(content);
            preimage.append(name).append('\t').append(hash).append('\n');
            if (!entries.isEmpty()) entries.append(',');
            entries.append("{\"path\":\"").append(name).append("\",\"sha256\":\"")
                .append(hash).append("\"}");
        }
        Files.writeString(directory.resolve("manifest.json"),
            "{\"schemaVersion\":1,\"releaseId\":\"synthetic-1\",\"catalogHash\":\""
                + sha256(preimage.toString()) + "\",\"files\":[" + entries + "]}");

        assertEquals("synthetic-1", CatalogSemanticParser.load(directory).release().releaseId());
        Files.writeString(directory.resolve("tools.json"), "{}");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogSemanticParser.load(directory));
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
