package com.agentfit.coreapi.catalog.discovery;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Reads skills.sh discovery metadata; never writes or activates a Catalog release. */
public final class SkillsShCandidateCollector {
    private static final ObjectMapper JSON = new ObjectMapper(
        new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final int MAX_BODY_BYTES = 2_097_152;
    private final Transport transport;
    private final URI baseUri;
    private final Supplier<String> tokenSupplier;
    private final Clock clock;

    public SkillsShCandidateCollector(Transport transport, URI baseUri,
                                      Supplier<String> tokenSupplier, Clock clock) {
        if (transport == null || baseUri == null || tokenSupplier == null || clock == null
            || !("https".equals(baseUri.getScheme()) && "skills.sh".equals(baseUri.getHost())
                || "http".equals(baseUri.getScheme()) && "127.0.0.1".equals(baseUri.getHost()))
            || baseUri.getUserInfo() != null || baseUri.getRawQuery() != null
            || !baseUri.getPath().isEmpty()) {
            throw new IllegalArgumentException("invalid skills.sh base URI");
        }
        this.transport = transport;
        this.baseUri = baseUri;
        this.tokenSupplier = tokenSupplier;
        this.clock = clock;
    }

    public static SkillsShCandidateCollector production(Supplier<String> oidcTokenSupplier) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        Transport transport = (uri, token) -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                return new Response(response.statusCode(), body.readNBytes(MAX_BODY_BYTES + 1));
            }
        };
        return new SkillsShCandidateCollector(transport,
            URI.create("https://skills.sh"), oidcTokenSupplier, Clock.systemUTC());
    }

    public List<SkillCandidate> search(String query, int limit) {
        if (query == null || query.strip().length() < 2 || query.length() > 100
            || limit < 1 || limit > 20) {
            throw new IllegalArgumentException("invalid discovery query");
        }
        String token = tokenSupplier.get();
        if (token == null || token.isBlank() || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) {
            throw new DiscoveryException("skills.sh credential unavailable");
        }
        String path = "/api/v1/skills/search?q="
            + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8) + "&limit=" + limit;
        JsonNode listing = get(path, token);
        JsonNode data = listing.path("data");
        if (!data.isArray() || data.size() > limit) throw new DiscoveryException("invalid skills.sh listing");
        List<SkillCandidate> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode item : data) {
            if (!item.isObject()) throw new DiscoveryException("invalid skills.sh item");
            JsonNode duplicate = item.get("isDuplicate");
            if (duplicate != null && !duplicate.isBoolean()) {
                throw new DiscoveryException("invalid skills.sh duplicate flag");
            }
            if (duplicate != null && duplicate.booleanValue()) continue;
            String id = required(item, "id");
            if (!id.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)?")
                || !ids.add(id)) throw new DiscoveryException("invalid skills.sh ID");
            String name = required(item, "name");
            String source = required(item, "source");
            String url = required(item, "url");
            if (!id.startsWith(source + "/") || !url.equals("https://skills.sh/" + id)) {
                throw new DiscoveryException("inconsistent skills.sh item");
            }
            JsonNode detail = get("/api/v1/skills/" + id, token);
            if (!id.equals(required(detail, "id"))) {
                throw new DiscoveryException("skills.sh detail identity mismatch");
            }
            String hash = required(detail, "hash");
            if (!hash.matches("[0-9a-f]{64}")) {
                throw new DiscoveryException("skills.sh snapshot hash unavailable");
            }
            result.add(new SkillCandidate(id, name, source, url, hash, clock.instant()));
        }
        return List.copyOf(result);
    }

    private JsonNode get(String path, String token) {
        try {
            Response response = transport.get(baseUri.resolve(path), token);
            if (response.statusCode() != 200) {
                throw new DiscoveryException("skills.sh HTTP " + response.statusCode());
            }
            byte[] bytes = response.body();
            if (bytes == null || bytes.length > MAX_BODY_BYTES) {
                throw new DiscoveryException("skills.sh response too large");
            }
            JsonNode parsed = JSON.readTree(bytes);
            if (parsed == null || !parsed.isObject()) {
                throw new DiscoveryException("invalid skills.sh response");
            }
            return parsed;
        } catch (IOException ex) {
            throw new DiscoveryException("skills.sh request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DiscoveryException("skills.sh request interrupted", ex);
        }
    }

    @FunctionalInterface
    public interface Transport {
        Response get(URI uri, String bearerToken) throws IOException, InterruptedException;
    }

    public record Response(int statusCode, byte[] body) {}

    private static String required(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new DiscoveryException("invalid skills.sh field: " + key);
        }
        return value.textValue();
    }

    public static final class DiscoveryException extends RuntimeException {
        public DiscoveryException(String message) { super(message); }
        public DiscoveryException(String message, Throwable cause) { super(message, cause); }
    }
}
