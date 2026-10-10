package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision.Status;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.RecommendationPreviewGate;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogPreviewAssemblerTest {
    @TempDir Path directory;

    @Test
    void assemblesVerifiedSelectionAndStaticBytesIntoOnePreview() throws IOException {
        String hash = writeBundle();
        CatalogPreviewRequest request = request(hash, new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
            List.of("example-tool"), PermissionPolicy.ASK_EACH_TIME);

        PreviewFingerprintResult preview = assemble(hash, request);
        assertEquals(1, preview.files().size());
        assertEquals("literal config\n", preview.files().getFirst().content());
        assertEquals("config/main.txt", preview.files().getFirst().relativePath());
        assertTrue(preview.fingerprint().matches("[0-9a-f]{64}"));
    }

    @Test
    void rejectsStaleCatalogBasisAndUnapprovedHash() throws IOException {
        String hash = writeBundle();
        CatalogPreviewRequest stale = request("0".repeat(64),
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), List.of("example-tool"),
            PermissionPolicy.ASK_EACH_TIME);
        assertThrows(CatalogPreviewAssembler.InvalidAssemblyException.class,
            () -> assemble(hash, stale));
        CatalogPreviewRequest current = request(hash,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), List.of("example-tool"),
            PermissionPolicy.ASK_EACH_TIME);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> assemble("f".repeat(64), current));
    }

    @Test
    void rejectsUnsupportedTargetDeniedRequiredPolicyAndDuplicateSelection() throws IOException {
        String hash = writeBundle();
        assertThrows(IllegalArgumentException.class, () -> assemble(
            hash, request(hash, new EnvironmentTarget("WINDOWS", "example-client", "2.0"),
                List.of("example-tool"), PermissionPolicy.ASK_EACH_TIME)));
        assertThrows(IllegalArgumentException.class, () -> assemble(
            hash, request(hash, new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
                List.of("example-tool"), PermissionPolicy.DENY)));
        assertThrows(CatalogPreviewAssembler.InvalidAssemblyException.class,
            () -> assemble(hash, request(hash,
                new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
                List.of("example-tool", "example-tool"), PermissionPolicy.ASK_EACH_TIME)));
    }

    @Test
    void refusesPreviewWhenOptionalDeniedPolicyCannotBeRemovedFromStaticOutput() throws IOException {
        String hash = writeBundle("literal config\n", false);
        CatalogPreviewRequest denied = request(hash,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
            List.of("example-tool"), PermissionPolicy.DENY);

        assertThrows(CatalogPreviewAssembler.InvalidAssemblyException.class,
            () -> assemble(hash, denied));
    }

    @Test
    void screensProvidedOriginalBeforeReturningPreview() throws IOException {
        String hash = writeBundle();
        CatalogPreviewRequest base = request(hash, new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
            List.of("example-tool"), PermissionPolicy.ASK_EACH_TIME);
        CatalogPreviewRequest supplied = new CatalogPreviewRequest(base.basis(), base.recommendationId(),
            base.target(), base.selectedToolKeys(), base.permissionSelections(),
            ExistingState.PROVIDED,
            List.of(new PreviewInputFile("main", "config/main.txt", "api_key=literal-secret\n")),
            base.generatorVersion());
        assertThrows(IllegalArgumentException.class,
            () -> assemble(hash, supplied));
    }

    @Test
    void rejectsGeneratedTemplateBeyondPreviewBudget() throws IOException {
        String hash = writeBundle("a".repeat(100_001));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> assemble(hash, request(hash,
                new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
                List.of("example-tool"), PermissionPolicy.ASK_EACH_TIME)));
    }

    @Test
    void rejectsRecommendationOutsideCurrentProjectSelectionBeforeCatalogLoad() {
        CatalogPreviewRequest request = request("a".repeat(64),
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
            List.of("example-tool"), PermissionPolicy.ASK_EACH_TIME);
        StoredRecommendationState unrelated = new StoredRecommendationState("rec-other", request.basis(),
            Status.RECOMMENDED, List.of("example-tool"));
        assertThrows(RecommendationPreviewGate.InvalidRecommendationException.class,
            () -> CatalogPreviewAssembler.assemble(directory, "a".repeat(64), unrelated,
                request.basis(), request.target(), request));
    }

    @Test
    void rejectsClientSuppliedTargetThatDiffersFromTrustedEnvironment() throws IOException {
        String hash = writeBundle();
        CatalogPreviewRequest request = request(hash,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"),
            List.of("example-tool"), PermissionPolicy.ASK_EACH_TIME);
        assertThrows(CatalogPreviewAssembler.InvalidAssemblyException.class,
            () -> assemble(hash, request, new EnvironmentTarget("MACOS", "example-client", "1.0")));
        assertThrows(CatalogPreviewAssembler.InvalidAssemblyException.class,
            () -> assemble(hash, request, null));
    }

    private PreviewFingerprintResult assemble(String approvedHash, CatalogPreviewRequest request) {
        return assemble(approvedHash, request, request.target());
    }

    private PreviewFingerprintResult assemble(String approvedHash, CatalogPreviewRequest request,
                                              EnvironmentTarget trustedTarget) {
        StoredRecommendationState stored = new StoredRecommendationState("rec-1", request.basis(),
            Status.RECOMMENDED, List.of("example-tool"));
        return CatalogPreviewAssembler.assemble(directory, approvedHash, stored, request.basis(),
            trustedTarget, request);
    }

    private CatalogPreviewRequest request(String hash, EnvironmentTarget target, List<String> selected,
                                           PermissionPolicy policy) {
        return new CatalogPreviewRequest(new PreviewBasis("project-1", 1,
            "profile-1", 1, "event-1", 0, 0, 1, "synthetic-preview", hash),
            "rec-1", target, selected,
            List.of(new PermissionSelection("example-tool", "network", policy)),
            ExistingState.UNKNOWN, List.of(), "static-v1");
    }

    private String writeBundle() throws IOException {
        return writeBundle("literal config\n");
    }

    private String writeBundle(String templateContent) throws IOException {
        return writeBundle(templateContent, true);
    }

    private String writeBundle(String templateContent, boolean requiredPermission) throws IOException {
        String capabilities = CapabilityKey.keys().stream().sorted()
            .map(key -> "{\"key\":\"" + key + "\"}")
            .reduce((a, b) -> a + "," + b).orElseThrow();
        Map<String, String> files = new HashMap<>();
        files.put("capabilities.json", "{\"schemaVersion\":1,\"items\":[" + capabilities + "]}");
        files.put("tools.json", "{\"schemaVersion\":1,\"items\":[{\"key\":\"example-tool\","
            + "\"version\":\"1.0\",\"capabilityKeys\":[\"cap_document_reference\"],"
            + "\"includedComponentKeys\":[\"example-component\"]}]}");
        files.put("support-matrix.json", "{\"schemaVersion\":1,\"items\":[{\"key\":\"support-1\","
            + "\"toolKey\":\"example-tool\",\"osFamily\":\"WINDOWS\",\"clientId\":\"example-client\","
            + "\"clientVersion\":\"1.0\",\"documentation\":\"PASS\",\"format\":\"PASS\","
            + "\"standalone\":\"PASS\",\"evidenceUrl\":\"https://example.org/support\","
            + "\"checkedAt\":\"2026-10-06\"}]}");
        files.put("relations.json", "{\"schemaVersion\":1,\"dependencies\":[],\"conflicts\":[],"
            + "\"verifiedCombinations\":[]}");
        files.put("permissions.json", "{\"schemaVersion\":1,\"items\":[{\"toolKey\":\"example-tool\","
            + "\"mappingKey\":\"network\",\"required\":" + requiredPermission + ","
            + "\"supportedPolicies\":[\"ASK_EACH_TIME\",\"DENY\"],"
            + "\"evidenceUrl\":\"https://example.org/permission\",\"checkedAt\":\"2026-10-06\"}]}");
        files.put("client-capabilities.json", "{\"schemaVersion\":1,\"items\":[]}");
        files.put("templates/index.json", "{\"schemaVersion\":1,\"items\":[{"
            + "\"toolKey\":\"example-tool\",\"targetKey\":\"main\","
            + "\"relativePath\":\"config/main.txt\",\"sourcePath\":\"templates/main.txt\"}]}");
        files.put("templates/main.txt", templateContent);

        StringBuilder preimage = new StringBuilder("agentfit-catalog-v1\nsynthetic-preview\n");
        StringBuilder entries = new StringBuilder();
        for (String name : files.keySet().stream().sorted().toList()) {
            Path path = directory.resolve(name);
            Files.createDirectories(path.getParent());
            Files.writeString(path, files.get(name));
            String fileHash = sha256(files.get(name));
            preimage.append(name).append('\t').append(fileHash).append('\n');
            if (!entries.isEmpty()) entries.append(',');
            entries.append("{\"path\":\"").append(name).append("\",\"sha256\":\"")
                .append(fileHash).append("\"}");
        }
        String hash = sha256(preimage.toString());
        Files.writeString(directory.resolve("manifest.json"), "{\"schemaVersion\":1,"
            + "\"releaseId\":\"synthetic-preview\",\"catalogHash\":\"" + hash
            + "\",\"files\":[" + entries + "]}");
        return hash;
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
