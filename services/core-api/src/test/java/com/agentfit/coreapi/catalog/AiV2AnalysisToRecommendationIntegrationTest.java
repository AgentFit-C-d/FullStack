package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.domain.analysis.client.HttpAiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.analysis.service.HmacKeyRing;
import com.agentfit.coreapi.domain.analysis.service.HmacRequestSigner;
import com.agentfit.coreapi.global.exception.ProfileNotConfirmedException;
import com.agentfit.coreapi.integration.ab.AiCapabilityAnalyzerAdapter;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiV2AnalysisToRecommendationIntegrationTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void draftIsBlockedButConfirmedSnapshotCanCallHmacNeedsAndCreateBRecommendation() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        AtomicInteger saves = new AtomicInteger();
        var blocked = new RecommendationCreationService(
            projectId -> { throw new ProfileNotConfirmedException(); },
            input -> fail("B must not call AI before confirmation"),
            () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
            (snapshot, basis, result) -> fail("B must not persist before confirmation"));
        assertThrows(ProfileNotConfirmedException.class, () -> blocked.create("project-1", expected()));

        AtomicBoolean hmacRequestReceived = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/skills/needs", exchange -> {
            hmacRequestReceived.set(exchange.getRequestHeaders().getFirst("X-AgentFit-Signature") != null);
            Map<?, ?> request = new ObjectMapper().readValue(exchange.getRequestBody().readAllBytes(), Map.class);
            Map<?, ?> payload = (Map<?, ?>) request.get("input");
            List<?> facts = (List<?>) payload.get("facts");
            assertTrue(facts.contains(Map.of("id", "profile.projectType", "field", "project_type", "value", "web")));
            assertTrue(facts.contains(Map.of("id", "profile.domain", "field", "domain", "value", "education")));
            assertTrue(facts.contains(Map.of("id", "developer.responsibility.0", "field", "features", "value", "API 검증")));
            List<Map<String, Object>> claims = CapabilityKey.keys().stream().sorted().map(key -> Map.of(
                "capabilityKey", key,
                "need", key.equals("cap_document_reference") ? "REQUIRED" : "OPTIONAL",
                "reason", "confirmed profile",
                "sourceFields", List.of("profile.item.features.0"))).toList();
            byte[] response = new ObjectMapper().writeValueAsBytes(Map.of(
                "requestId", request.get("requestId"), "generation", request.get("generation"),
                "contractVersion", request.get("contractVersion"),
                "result", Map.of("claims", claims, "questions", List.of())));
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var ring = HmacKeyRing.from(new HmacKeyRing.Configuration("current", "secret", null, null, null, Instant.now()));
            var client = new HttpAiAnalysisClient(java.net.http.HttpClient.newHttpClient(), new ObjectMapper(),
                new HmacRequestSigner(ring), URI.create("http://localhost:" + server.getAddress().getPort() + "/analysis"),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/internal/v1/skills/needs"),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/rank"));
            var service = new RecommendationCreationService(
                AiV2AnalysisToRecommendationIntegrationTest::confirmedSnapshot,
                new AiCapabilityAnalyzerAdapter(client, new ObjectMapper()),
                () -> new RecommendationCreationService.ApprovedCatalog(directory, hash),
                (snapshot, basis, result) -> { saves.incrementAndGet(); return "recommendation-1"; });
            var created = service.create("project-1", expected());
            assertEquals("recommendation-1", created.recommendationId());
            assertEquals(CapabilityKey.keys().size(), created.result().capabilities().size());
            assertEquals(1, saves.get());
            assertTrue(hmacRequestReceived.get());
        } finally { server.stop(0); }
    }

    private static RecommendationCreationService.OwnerCheckedSnapshot confirmedSnapshot(String projectId) {
        return new RecommendationCreationService.OwnerCheckedSnapshot(projectId, 7, "confirmed-1", 1,
            "confirmation-1", 1, 1, 1, Set.of("profile.item.features.0", "profile.projectType", "profile.domain", "developer.responsibility.0"),
            Map.of("profile.item.features.0", "document search", "profile.projectType", "web", "profile.domain", "education",
                "developer.responsibility.0", "API 검증"), false,
            new EnvironmentTarget("WINDOWS", "client", "1.0"), Map.of());
    }

    private static RecommendationCreationService.ExpectedVersions expected() {
        return new RecommendationCreationService.ExpectedVersions(7, 1, 1);
    }
}
