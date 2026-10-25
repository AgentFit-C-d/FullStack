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
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
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
    private final boolean publicExperimental;

    public SkillsShCandidateCollector(Transport transport, URI baseUri,
                                      Supplier<String> tokenSupplier, Clock clock) {
        this(transport, baseUri, tokenSupplier, clock, false);
    }

    private SkillsShCandidateCollector(Transport transport, URI baseUri,
                                       Supplier<String> tokenSupplier, Clock clock,
                                       boolean publicExperimental) {
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
        this.publicExperimental = publicExperimental;
    }

    /** Undocumented public endpoints: discovery only; no Catalog approval or stability guarantee. */
    public static SkillsShCandidateCollector publicExperimental() {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        Transport transport = (uri, ignored) -> {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json").GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                return new Response(response.statusCode(), body.readNBytes(MAX_BODY_BYTES + 1));
            }
        };
        return publicExperimental(transport, URI.create("https://skills.sh"), Clock.systemUTC());
    }

    static SkillsShCandidateCollector publicExperimental(Transport transport, URI baseUri, Clock clock) {
        return new SkillsShCandidateCollector(transport, baseUri, () -> "", clock, true);
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
        if (!publicExperimental && (token == null || token.isBlank()
            || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0)) {
            throw new DiscoveryException("skills.sh credential unavailable");
        }
        String path = (publicExperimental ? "/api/search?q=" : "/api/v1/skills/search?q=")
            + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8) + "&limit=" + limit;
        JsonNode listing = get(path, token);
        JsonNode data = listing.path(publicExperimental ? "skills" : "data");
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
            String url = publicExperimental ? "https://skills.sh/" + id : required(item, "url");
            if (!source.equals(id.substring(0, id.lastIndexOf('/')))
                || !url.equals("https://skills.sh/" + id)) {
                throw new DiscoveryException("inconsistent skills.sh item");
            }
            JsonNode detail = get((publicExperimental ? "/api/download/" : "/api/v1/skills/") + id, token);
            if (!publicExperimental && (!id.equals(required(detail, "id"))
                || !source.equals(required(detail, "source")))) {
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

    /** Fetches reviewable Markdown files for one pinned candidate; never approves or activates it. */
    public SkillPackage download(SkillCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("candidate required");
        String token = tokenSupplier.get();
        if (!publicExperimental && (token == null || token.isBlank()
            || token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0)) {
            throw new DiscoveryException("skills.sh credential unavailable");
        }
        JsonNode detail = get((publicExperimental ? "/api/download/" : "/api/v1/skills/")
            + candidate.externalId(), token);
        if (!publicExperimental && (!candidate.externalId().equals(required(detail, "id"))
            || !candidate.source().equals(required(detail, "source")))) {
            throw new DiscoveryException("skills.sh detail identity mismatch");
        }
        String hash = required(detail, "hash");
        if (!candidate.contentHash().equals(hash)) {
            throw new DiscoveryException("skills.sh content changed since discovery");
        }
        JsonNode files = detail.path("files");
        if (!files.isArray() || files.isEmpty() || files.size() > 20) {
            throw new DiscoveryException("invalid skills.sh file list");
        }
        Map<String, String> contents = new LinkedHashMap<>();
        Set<String> foldedPaths = new HashSet<>();
        long totalBytes = 0;
        for (JsonNode file : files) {
            String path = required(file, "path");
            String body = required(file, "contents");
            if (!safeMarkdownPath(path) || !foldedPaths.add(path.toLowerCase(Locale.ROOT))) {
                throw new DiscoveryException("unsafe skills.sh file path");
            }
            int bytes = body.getBytes(StandardCharsets.UTF_8).length;
            totalBytes += bytes;
            if (bytes > 1_048_576 || totalBytes > 1_048_576) {
                throw new DiscoveryException("skills.sh package too large");
            }
            contents.put(path, body);
        }
        if (!contents.containsKey("SKILL.md")) {
            throw new DiscoveryException("skills.sh package lacks SKILL.md");
        }
        return new SkillPackage(hash, Map.copyOf(contents));
    }

    private static boolean safeMarkdownPath(String path) {
        if (path.length() > 200 || !path.matches("[A-Za-z0-9._/-]+")
            || path.startsWith("/") || path.endsWith("/") || path.contains("//")
            || !path.endsWith(".md")) return false;
        for (String part : path.split("/")) {
            if (part.equals(".") || part.equals("..")) return false;
        }
        return true;
    }

    public record SkillPackage(String contentHash, Map<String, String> files) {
        public SkillPackage { files = Map.copyOf(files); }
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
