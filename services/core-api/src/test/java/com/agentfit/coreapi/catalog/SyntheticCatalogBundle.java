package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Shared test-only release with one exactly supported tool. Never a product Catalog. */
final class SyntheticCatalogBundle {
    private SyntheticCatalogBundle() {}

    static String write(Path directory) throws Exception {
        return write(directory, true, "literal config\n");
    }

    static String write(Path directory, boolean includeTemplate) throws Exception {
        return write(directory, includeTemplate, "literal config\n");
    }

    static String write(Path directory, boolean includeTemplate, String templateContent) throws Exception {
        return write(directory, includeTemplate, templateContent, false, false);
    }

    static String writeWithFallback(Path directory, String firstTemplateContent) throws Exception {
        return write(directory, true, firstTemplateContent, true, false);
    }

    static String writeWithOversizedCombination(Path directory) throws Exception {
        return write(directory, true, "literal config\n", true, true);
    }

    private static String write(Path directory, boolean includeTemplate, String templateContent,
                                boolean includeFallback, boolean oversizedCombination) throws Exception {
        String capabilities = CapabilityKey.keys().stream().sorted()
            .map(key -> "{\"key\":\"" + key + "\"}")
            .reduce((a, b) -> a + "," + b).orElseThrow();
        Map<String, String> files = new HashMap<>();
        files.put("capabilities.json", "{\"schemaVersion\":1,\"items\":[" + capabilities + "]}");
        files.put("tools.json", "{\"schemaVersion\":1,\"items\":[{\"key\":\"example-tool\","
            + "\"version\":\"1.0\",\"capabilityKeys\":[\"cap_document_reference\"],"
            + "\"includedComponentKeys\":[\"example-component\"]}"
            + (includeFallback ? ", {\"key\":\"fallback-tool\",\"version\":\"1.0\","
                + "\"capabilityKeys\":[\"cap_document_reference\"],"
                + "\"includedComponentKeys\":[\"fallback-component\"]}" : "") + "]}");
        files.put("support-matrix.json", "{\"schemaVersion\":1,\"items\":[{\"key\":\"support-1\","
            + "\"toolKey\":\"example-tool\",\"osFamily\":\"WINDOWS\",\"clientId\":\"example-client\","
            + "\"clientVersion\":\"1.0\",\"documentation\":\"PASS\",\"format\":\"PASS\","
            + "\"standalone\":\"PASS\",\"evidenceUrl\":\"https://example.org/review\","
            + "\"checkedAt\":\"2026-10-09\"}"
            + (includeFallback ? ", {\"key\":\"support-2\",\"toolKey\":\"fallback-tool\","
                + "\"osFamily\":\"WINDOWS\",\"clientId\":\"example-client\","
                + "\"clientVersion\":\"1.0\",\"documentation\":\"PASS\","
                + "\"format\":\"PASS\",\"standalone\":\"PASS\","
                + "\"evidenceUrl\":\"https://example.org/review-fallback\","
                + "\"checkedAt\":\"2026-10-09\"}" : "") + "]}");
        files.put("relations.json", "{\"schemaVersion\":1,\"dependencies\":[],\"conflicts\":[],"
            + "\"verifiedCombinations\":["
            + (oversizedCombination ? "{\"toolKeys\":[\"example-tool\",\"fallback-tool\"],"
                + "\"osFamily\":\"WINDOWS\",\"clientId\":\"example-client\","
                + "\"clientVersion\":\"1.0\",\"evidenceUrl\":\"https://example.org/combination\","
                + "\"checkedAt\":\"2026-10-09\"}" : "") + "]}");
        files.put("permissions.json", "{\"schemaVersion\":1,\"items\":[{\"toolKey\":\"example-tool\","
            + "\"mappingKey\":\"read\",\"required\":false,"
            + "\"supportedPolicies\":[\"ASK_EACH_TIME\",\"DENY\"],"
            + "\"evidenceUrl\":\"https://example.org/permission\",\"checkedAt\":\"2026-10-09\"}"
            + (includeFallback ? ", {\"toolKey\":\"fallback-tool\","
                + "\"mappingKey\":\"read\",\"required\":false,"
                + "\"supportedPolicies\":[\"ASK_EACH_TIME\",\"DENY\"],"
                + "\"evidenceUrl\":\"https://example.org/permission-fallback\","
                + "\"checkedAt\":\"2026-10-09\"}" : "") + "]}");
        files.put("client-capabilities.json", "{\"schemaVersion\":1,\"items\":[]}");
        if (includeTemplate) {
            StringBuilder index = new StringBuilder("{\"schemaVersion\":1,\"items\":[{"
                + "\"toolKey\":\"example-tool\",\"targetKey\":\"main\","
                + "\"relativePath\":\"config/main.txt\",\"sourcePath\":\"templates/main.txt\"}"
                + (includeFallback ? ", {\"toolKey\":\"fallback-tool\","
                    + "\"targetKey\":\"fallback\",\"relativePath\":\"config/fallback.txt\","
                    + "\"sourcePath\":\"templates/fallback.txt\"}" : ""));
            files.put("templates/main.txt", templateContent);
            if (includeFallback) files.put("templates/fallback.txt", "fallback config\n");
            if (oversizedCombination) {
                for (int number = 0; number < 10; number++) {
                    for (String tool : new String[] {"example-tool", "fallback-tool"}) {
                        String path = "guides/" + tool + "-" + number + ".txt";
                        index.append(",{\"toolKey\":\"").append(tool)
                            .append("\",\"targetKey\":\"").append(tool).append("-").append(number)
                            .append("\",\"relativePath\":\"guide/").append(tool).append("-")
                            .append(number).append(".txt\",\"sourcePath\":\"").append(path).append("\"}");
                        files.put(path, "reviewed guide\n");
                    }
                }
            }
            files.put("templates/index.json", index.append("]}").toString());
        }
        StringBuilder preimage = new StringBuilder("agentfit-catalog-v1\nsynthetic-recommendation\n");
        StringBuilder entries = new StringBuilder();
        for (String name : files.keySet().stream().sorted().toList()) {
            Path file = directory.resolve(name);
            Files.createDirectories(file.getParent());
            Files.writeString(file, files.get(name));
            String fileHash = sha256(files.get(name));
            preimage.append(name).append('\t').append(fileHash).append('\n');
            if (!entries.isEmpty()) entries.append(',');
            entries.append("{\"path\":\"").append(name).append("\",\"sha256\":\"")
                .append(fileHash).append("\"}");
        }
        String hash = sha256(preimage.toString());
        Files.writeString(directory.resolve("manifest.json"), "{\"schemaVersion\":1,"
            + "\"releaseId\":\"synthetic-recommendation\",\"catalogHash\":\"" + hash
            + "\",\"files\":[" + entries + "]}");
        return hash;
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
