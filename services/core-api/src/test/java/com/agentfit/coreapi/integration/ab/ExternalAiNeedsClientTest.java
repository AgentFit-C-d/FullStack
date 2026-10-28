package com.agentfit.coreapi.integration.ab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentfit.coreapi.domain.analysis.client.AiServiceFailureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExternalAiNeedsClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsBearerBudgetAndMappedFactsAndRestoresSourcePaths() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> request = new AtomicReference<>();
        start((exchange) -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"needs\":[{\"id\":\"n1\",\"name\":\"테스트 작성\",\"query\":\"test development\",\"sourceFields\":[\"features:0\",\"work:0\"]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        var result = client().discover(Map.of(
            "profile.item.features.0", "테스트 작성",
            "developer.responsibility.0", "품질 담당"),
            Instant.parse("2030-01-01T00:00:00Z"), "project_1", "lease_1");

        assertEquals("Bearer 01234567890123456789012345678901", authorization.get());
        var payload = mapper.readTree(request.get());
        assertEquals("2030-01-01T00:00:00Z", payload.path("deadline").asText());
        assertEquals("project_1", payload.path("budget").path("targetId").asText());
        assertEquals("lease_1", payload.path("budget").path("leaseId").asText());
        assertEquals("work:0", payload.path("facts").get(0).path("id").asText());
        assertEquals("features:0", payload.path("facts").get(1).path("id").asText());
        assertEquals("profile.item.features.0", result.get(0).sourceFields().get(0));
        assertEquals("developer.responsibility.0", result.get(0).sourceFields().get(1));
    }

    @Test
    void rejectsUnknownReturnedFactId() throws Exception {
        start((exchange) -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"needs\":[{\"id\":\"n1\",\"name\":\"검증\",\"query\":\"test development\",\"sourceFields\":[\"features:99\"]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        var error = assertThrows(AiServiceFailureException.class, () -> client().discover(
            Map.of("profile.item.features.0", "테스트 작성"),
            Instant.parse("2030-01-01T00:00:00Z"), "project_1", "lease_1"));
        assertEquals(AiServiceFailureException.Kind.CONTRACT, error.kind());
    }

    @Test
    void malformedAiJsonIsAContractFailure() throws Exception {
        start((exchange) -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{broken".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        var error = assertThrows(AiServiceFailureException.class, () -> client().discover(
            Map.of("profile.item.features.0", "테스트 작성"),
            Instant.parse("2030-01-01T00:00:00Z"), "project_1", "lease_1"));
        assertEquals(AiServiceFailureException.Kind.CONTRACT, error.kind());
    }

    @Test
    void nullAiJsonIsAContractFailure() throws Exception {
        start((exchange) -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "null".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        var error = assertThrows(AiServiceFailureException.class, () -> client().discover(
            Map.of("profile.item.features.0", "테스트 작성"),
            Instant.parse("2030-01-01T00:00:00Z"), "project_1", "lease_1"));
        assertEquals(AiServiceFailureException.Kind.CONTRACT, error.kind());
    }

    @Test
    void rejectsUnsupportedFactsBeforeSending() {
        var error = assertThrows(AiServiceFailureException.class, () -> client(URI.create("http://127.0.0.1:1/internal/v1/skills/needs")).discover(
            Map.of("environment.clientVersion", "2.1.310"),
            Instant.parse("2030-01-01T00:00:00Z"), "project_1", "lease_1"));
        assertEquals(AiServiceFailureException.Kind.INPUT, error.kind());
        assertTrue(error.code().startsWith("AI_NEEDS_"));
    }

    @Test
    void expiredDeadlineDoesNotStartAnAiRequest() {
        var error = assertThrows(AiServiceFailureException.class, () -> client(URI.create("http://127.0.0.1:1/internal/v1/skills/needs")).discover(
            Map.of("profile.item.features.0", "테스트 작성"),
            Instant.parse("2020-01-01T00:00:00Z"), "project_1", "lease_1"));
        assertEquals(AiServiceFailureException.Kind.INPUT, error.kind());
    }

    private ExternalAiNeedsClient client() {
        return client(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/internal/v1/skills/needs"));
    }

    private ExternalAiNeedsClient client(URI endpoint) {
        return new ExternalAiNeedsClient(HttpClient.newHttpClient(), mapper, endpoint,
            "01234567890123456789012345678901");
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/skills/needs", handler);
        server.start();
    }
}
