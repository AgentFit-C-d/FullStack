package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CatalogRecommendationWorkflowTest {
    @TempDir Path directory;

    @Test
    void producesRecommendationFromApprovedCatalogAndCompleteAiAssessment() throws Exception {
        String approvedHash = writeBundle();
        var outcome = CatalogRecommendationWorkflow.evaluate(directory, approvedHash, request());
        assertEquals(RecommendationDecision.Status.RECOMMENDED, outcome.decision().status());
        assertEquals(List.of("example-tool"), outcome.decision().toolKeys());
        assertEquals("synthetic-recommendation", outcome.catalogReleaseId());
        assertEquals(approvedHash, outcome.catalogHash());
        assertEquals(List.of(), outcome.clarificationQuestions());
    }

    @Test
    void rejectsUnapprovedCatalogAndIncompleteAiAssessment() throws Exception {
        String approvedHash = writeBundle();
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogRecommendationWorkflow.evaluate(directory, "f".repeat(64), request()));
        var incomplete = new CatalogRecommendationWorkflow.Request(
            request().claims().subList(0, 1), List.of(), Set.of("profile.features"),
            false, new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
        assertThrows(IllegalArgumentException.class,
            () -> CatalogRecommendationWorkflow.evaluate(directory, approvedHash, incomplete));
    }

    @Test
    void keepsAiQuestionsAndReturnsNeedsInformationForUndeterminedCapability() throws Exception {
        String approvedHash = writeBundle();
        var claims = request().claims().stream().map(claim ->
            claim.capabilityKey().equals("cap_document_reference")
                ? new AiCapabilityIntake.Claim(claim.capabilityKey(), AiCapabilityIntake.Need.UNDETERMINED,
                    "Need project context", List.of()) : claim).toList();
        var question = new AiCapabilityIntake.Question("profile.features", "What documents are needed?");
        var input = new CatalogRecommendationWorkflow.Request(claims, List.of(question),
            Set.of("profile.features"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
        var outcome = CatalogRecommendationWorkflow.evaluate(directory, approvedHash, input);
        assertEquals(RecommendationDecision.Status.NEEDS_INFORMATION, outcome.decision().status());
        assertEquals(List.of(question), outcome.clarificationQuestions());
    }

    @Test
    void treatsUnknownInstalledVersionAsQuestionInsteadOfRejectingRequest() throws Exception {
        String approvedHash = writeBundle();
        Map<String, String> installed = new HashMap<>();
        installed.put("example-tool", null);
        var input = new CatalogRecommendationWorkflow.Request(request().claims(), List.of(),
            Set.of("profile.features"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), installed);
        var outcome = CatalogRecommendationWorkflow.evaluate(directory, approvedHash, input);
        assertEquals(RecommendationDecision.Status.NEEDS_INFORMATION, outcome.decision().status());
        assertTrue(outcome.decision().questionCodes().contains("installed_component_version"));
    }

    @Test
    void invalidAiClaimIsRejectedByIntakeWithoutConstructorNullPointer() throws Exception {
        String approvedHash = writeBundle();
        var claims = new ArrayList<>(request().claims());
        claims.set(0, null);
        var input = new CatalogRecommendationWorkflow.Request(claims, List.of(),
            Set.of("profile.features"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
        assertThrows(IllegalArgumentException.class,
            () -> CatalogRecommendationWorkflow.evaluate(directory, approvedHash, input));
    }

    private CatalogRecommendationWorkflow.Request request() {
        var claims = CapabilityKey.keys().stream().sorted().map(key ->
            new AiCapabilityIntake.Claim(key,
                key.equals("cap_document_reference") ? AiCapabilityIntake.Need.REQUIRED
                    : AiCapabilityIntake.Need.OPTIONAL,
                "Based on confirmed project profile", List.of("profile.features"))).toList();
        return new CatalogRecommendationWorkflow.Request(claims, List.of(),
            Set.of("profile.features"), false,
            new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of());
    }

    private String writeBundle() throws Exception {
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
            + "\"standalone\":\"PASS\",\"evidenceUrl\":\"https://example.org/review\","
            + "\"checkedAt\":\"2026-10-09\"}]}");
        files.put("relations.json", "{\"schemaVersion\":1,\"dependencies\":[],\"conflicts\":[],"
            + "\"verifiedCombinations\":[]}");
        files.put("permissions.json", "{\"schemaVersion\":1,\"items\":[]}");
        files.put("client-capabilities.json", "{\"schemaVersion\":1,\"items\":[]}");
        StringBuilder preimage = new StringBuilder("agentfit-catalog-v1\nsynthetic-recommendation\n");
        StringBuilder entries = new StringBuilder();
        for (String name : files.keySet().stream().sorted().toList()) {
            Files.writeString(directory.resolve(name), files.get(name));
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
