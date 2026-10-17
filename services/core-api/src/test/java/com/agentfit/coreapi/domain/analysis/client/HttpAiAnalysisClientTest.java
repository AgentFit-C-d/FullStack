package com.agentfit.coreapi.domain.analysis.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.analysis.service.HmacKeyRing;
import com.agentfit.coreapi.domain.analysis.service.HmacRequestSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpAiAnalysisClientTest {
    @Test
    void signsExactBodyAndUsesNeedsPath() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(exchange.getRequestBody().readAllBytes());
            byte[] response = "{\"requestId\":\"r-1\",\"generation\":2,\"contractVersion\":\"v1\",\"result\":{\"claims\":[],\"questions\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            HmacKeyRing ring = HmacKeyRing.from(new HmacKeyRing.Configuration(
                "current", "secret", null, null, null, Instant.now()));
            var client = new HttpAiAnalysisClient(java.net.http.HttpClient.newHttpClient(),
                new ObjectMapper(), new HmacRequestSigner(ring),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/internal/v1/analysis"),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/internal/v1/skills/needs"),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/internal/v1/skills/rank"));
            var context = new AnalysisAttemptContext("r-1", "p-1", 2L, "v1",
                Instant.now().plusSeconds(30), "PROCESSING");

            client.needs(context, Map.of("facts", java.util.List.of()));

            assertEquals("/internal/v1/skills/needs", path.get());
            assertFalse(body.get().length == 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsProviderAndContractFailuresWithoutTurningThemIntoEmptyResults() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] response = "not-json".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var client = client(server, "/analysis");
            var failure = assertThrows(AiServiceFailureException.class,
                () -> client.analyze(context(), Map.of("kind", "text")));
            assertEquals(AiServiceFailureException.Kind.CONTRACT, failure.kind());
            assertEquals("AI_INVALID_RESPONSE", failure.code());
        } finally { server.stop(0); }
    }

    @Test
    void mapsHttpProviderFailureSeparately() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var failure = assertThrows(AiServiceFailureException.class,
                () -> client(server, "/analysis").analyze(context(), Map.of()));
            assertEquals(AiServiceFailureException.Kind.PROVIDER, failure.kind());
            assertEquals("AI_HTTP_500", failure.code());
        } finally { server.stop(0); }
    }

    private static HttpAiAnalysisClient client(HttpServer server, String path) {
        HmacKeyRing ring = HmacKeyRing.from(new HmacKeyRing.Configuration(
            "current", "secret", null, null, null, Instant.now()));
        return new HttpAiAnalysisClient(java.net.http.HttpClient.newHttpClient(),
            new ObjectMapper(), new HmacRequestSigner(ring),
            URI.create("http://localhost:" + server.getAddress().getPort() + path));
    }

    private static AnalysisAttemptContext context() {
        return new AnalysisAttemptContext("r-1", "p-1", 2L, "v1",
            Instant.now().plusSeconds(30), "PROCESSING");
    }
}
