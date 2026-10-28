package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.domain.analysis.client.AiServiceFailureException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** B-only client for the separate AI repository's search needs. These are not Capability claims. */
public final class ExternalAiNeedsClient {
    private static final Pattern PROFILE_PATH = Pattern.compile("^profile\\.item\\.([a-z_]+)\\.([0-9]{1,3})$");
    private static final Pattern WORK_PATH = Pattern.compile("^developer\\.responsibility\\.([0-9]{1,3})$");
    private static final Pattern BUDGET_ID = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");
    private static final Pattern NEED_ID = Pattern.compile("^n[1-6]$");
    private static final Pattern QUERY = Pattern.compile("^[a-z][a-z0-9 -]{1,99}$");
    private static final Set<String> AI_FIELDS = Set.of("frontend", "backend", "ai", "database",
        "deployment", "features", "external_integrations", "work", "purpose");

    public record SearchNeed(String id, String name, String query, List<String> sourceFields) {}

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String token;

    public ExternalAiNeedsClient(HttpClient http, ObjectMapper mapper, URI endpoint, String token) {
        if (http == null || mapper == null || endpoint == null || token == null || token.length() < 32
            || endpoint.getPath() == null || !endpoint.getPath().endsWith("/internal/v1/skills/needs")) {
            throw new IllegalArgumentException("invalid external AI needs client configuration");
        }
        this.http = http;
        this.mapper = mapper;
        this.endpoint = endpoint;
        this.token = token;
    }

    public List<SearchNeed> discover(Map<String, String> sourceValues, Instant deadline,
                                     String targetId, String leaseId) {
        if (sourceValues == null || sourceValues.size() > 96 || deadline == null
            || !validId(targetId) || !validId(leaseId)) {
            throw inputFailure();
        }
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) throw inputFailure();
        Map<String, String> originalPaths = new HashMap<>();
        List<Map<String, String>> facts = new ArrayList<>();
        for (var entry : sourceValues.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String id = factId(entry.getKey());
            String value = entry.getValue();
            if (id == null || value == null || value.isBlank() || value.length() > 512
                || originalPaths.putIfAbsent(id, entry.getKey()) != null) {
                throw inputFailure();
            }
            facts.add(Map.of("id", id, "field", id.substring(0, id.indexOf(':')), "value", value));
        }
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("facts", facts);
            requestBody.put("deadline", deadline.toString());
            requestBody.put("budget", Map.of("targetId", targetId, "leaseId", leaseId));
            byte[] body = mapper.writeValueAsBytes(requestBody);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(remaining.compareTo(Duration.ofSeconds(90)) > 0
                    ? Duration.ofSeconds(90) : remaining)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                var kind = response.statusCode() >= 500
                    ? AiServiceFailureException.Kind.PROVIDER : AiServiceFailureException.Kind.INPUT;
                throw new AiServiceFailureException(kind, "AI_NEEDS_HTTP_" + response.statusCode(),
                    "external AI needs request failed");
            }
            if (response.body().length() > 196608) throw contractFailure();
            JsonNode result;
            try {
                result = mapper.readTree(response.body());
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw contractFailure();
            }
            return parse(result, originalPaths);
        } catch (AiServiceFailureException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiServiceFailureException(AiServiceFailureException.Kind.TRANSPORT,
                "AI_NEEDS_INTERRUPTED", "external AI needs request interrupted", ex);
        } catch (java.io.IOException ex) {
            throw new AiServiceFailureException(AiServiceFailureException.Kind.TRANSPORT,
                "AI_NEEDS_TRANSPORT_FAILURE", "external AI needs request failed", ex);
        }
    }

    private static List<SearchNeed> parse(JsonNode root, Map<String, String> originalPaths) {
        JsonNode nodes = root.path("needs");
        if (!root.isObject() || root.size() != 1 || !nodes.isArray() || nodes.size() > 6) {
            throw contractFailure();
        }
        List<SearchNeed> result = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (JsonNode node : nodes) {
            String id = node.path("id").asText();
            String name = node.path("name").asText();
            String query = node.path("query").asText();
            JsonNode sources = node.path("sourceFields");
            if (!node.isObject() || node.size() != 4 || !NEED_ID.matcher(id).matches()
                || !seen.add(id) || name.isBlank() || name.length() > 120
                || !QUERY.matcher(query).matches() || !sources.isArray()
                || sources.isEmpty() || sources.size() > 12) throw contractFailure();
            List<String> paths = new ArrayList<>();
            Set<String> sourceSeen = new java.util.HashSet<>();
            for (JsonNode source : sources) {
                if (!source.isTextual() || !sourceSeen.add(source.asText())) throw contractFailure();
                String path = originalPaths.get(source.asText());
                if (path == null) throw contractFailure();
                paths.add(path);
            }
            result.add(new SearchNeed(id, name, query, List.copyOf(paths)));
        }
        return List.copyOf(result);
    }

    private static String factId(String path) {
        if (path == null) return null;
        Matcher work = WORK_PATH.matcher(path);
        if (work.matches()) return "work:" + work.group(1);
        Matcher profile = PROFILE_PATH.matcher(path);
        if (profile.matches() && AI_FIELDS.contains(profile.group(1))) {
            return profile.group(1) + ":" + profile.group(2);
        }
        return null;
    }

    private static boolean validId(String value) {
        return value != null && BUDGET_ID.matcher(value).matches();
    }

    private static AiServiceFailureException inputFailure() {
        return new AiServiceFailureException(AiServiceFailureException.Kind.INPUT,
            "AI_NEEDS_INPUT_INVALID", "facts or budget context do not match external AI contract");
    }

    private static AiServiceFailureException contractFailure() {
        return new AiServiceFailureException(AiServiceFailureException.Kind.CONTRACT,
            "AI_NEEDS_RESPONSE_INVALID", "external AI needs response is invalid");
    }
}
