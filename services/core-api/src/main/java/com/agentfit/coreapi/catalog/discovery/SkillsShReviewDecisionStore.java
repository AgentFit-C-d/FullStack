package com.agentfit.coreapi.catalog.discovery;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local human triage only; a stored decision never approves Catalog support. */
public final class SkillsShReviewDecisionStore {
    private static final ObjectMapper JSON = new ObjectMapper(
        new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final int MAX_BYTES = 2_097_152;
    private static final Set<String> FIELDS = Set.of("externalId", "reviewedHash", "reviewedName",
        "disposition", "reviewerId", "reviewedAt");

    private SkillsShReviewDecisionStore() {}

    public static List<SkillsShReviewQueue.Decision> load(Path file) {
        if (file == null) throw new IllegalArgumentException("decision path required");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidDecisionsException("unsafe review decision file");
        }
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(file)) {
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) throw new InvalidDecisionsException("review decisions too large");
            JsonNode root = JSON.readTree(bytes);
            if (root == null || !root.isObject() || root.size() != 2
                || !root.path("schemaVersion").isInt() || root.path("schemaVersion").intValue() != 1
                || !root.path("decisions").isArray()) {
                throw new InvalidDecisionsException("invalid review decisions");
            }
            List<SkillsShReviewQueue.Decision> result = new ArrayList<>();
            Map<String, Boolean> ids = new HashMap<>();
            for (JsonNode item : root.path("decisions")) {
                if (!item.isObject() || item.size() != FIELDS.size()) {
                    throw new InvalidDecisionsException("invalid review decision entry");
                }
                for (String field : FIELDS) {
                    if (!item.has(field) || !item.path(field).isTextual()) {
                        throw new InvalidDecisionsException("invalid review decision entry");
                    }
                }
                var decision = new SkillsShReviewQueue.Decision(
                    item.path("externalId").textValue(), item.path("reviewedHash").textValue(),
                    item.path("reviewedName").textValue(),
                    SkillsShReviewQueue.Disposition.valueOf(item.path("disposition").textValue()),
                    item.path("reviewerId").textValue(), Instant.parse(item.path("reviewedAt").textValue()));
                if (ids.putIfAbsent(decision.externalId(), true) != null) {
                    throw new InvalidDecisionsException("duplicate review decision");
                }
                result.add(decision);
            }
            return List.copyOf(result);
        } catch (IOException | DateTimeParseException | IllegalArgumentException exception) {
            throw new InvalidDecisionsException("review decisions unreadable", exception);
        }
    }

    public static synchronized SkillsShReviewQueue.Decision record(
        Path snapshot, Path file, String externalId, SkillsShReviewQueue.Disposition disposition,
        String reviewerId, Clock clock) {
        if (file == null || clock == null) throw new IllegalArgumentException("review context required");
        List<SkillCandidate> candidates = SkillsShCandidateSnapshot.load(snapshot);
        SkillCandidate candidate = candidates.stream()
            .filter(item -> item.externalId().equals(externalId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("candidate not found"));
        List<SkillsShReviewQueue.Decision> old = load(file);
        SkillsShReviewQueue.classify(snapshot, old);
        var decision = new SkillsShReviewQueue.Decision(candidate.externalId(), candidate.contentHash(),
            candidate.name(), disposition, reviewerId, clock.instant());
        Map<String, SkillsShReviewQueue.Decision> updated = new HashMap<>();
        for (var item : old) updated.put(item.externalId(), item);
        updated.put(decision.externalId(), decision);
        List<SkillsShReviewQueue.Decision> sorted = updated.values().stream()
            .sorted(Comparator.comparing(SkillsShReviewQueue.Decision::externalId)).toList();
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode entries = root.putArray("decisions");
        for (var item : sorted) {
            ObjectNode entry = entries.addObject();
            entry.put("externalId", item.externalId());
            entry.put("reviewedHash", item.reviewedHash());
            entry.put("reviewedName", item.reviewedName());
            entry.put("disposition", item.disposition().name());
            entry.put("reviewerId", item.reviewerId());
            entry.put("reviewedAt", item.reviewedAt().toString());
        }
        Path temporary = null;
        try {
            Path target = file.toAbsolutePath().normalize();
            Path parent = target.getParent();
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".skills-review-", ".json");
            byte[] bytes = JSON.writeValueAsBytes(root);
            if (bytes.length > MAX_BYTES) throw new InvalidDecisionsException("review decisions too large");
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
            return decision;
        } catch (IOException exception) {
            throw new InvalidDecisionsException("review decision write failed", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* best effort */ }
            }
        }
    }

    public static final class InvalidDecisionsException extends RuntimeException {
        public InvalidDecisionsException(String message) { super(message); }
        public InvalidDecisionsException(String message, Throwable cause) { super(message, cause); }
    }
}
