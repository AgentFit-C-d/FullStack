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
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Stages external discoveries in a local JSON file, separate from verified Catalog releases. */
public final class SkillsShCandidateSnapshot {
    private static final ObjectMapper JSON = new ObjectMapper(
        new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final int MAX_BYTES = 2_097_152;
    private static final Set<String> FIELDS = Set.of("externalId", "name", "source", "sourceUrl",
        "contentHash", "observedAt");

    private SkillsShCandidateSnapshot() {}

    public static List<SkillCandidate> load(Path snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot path required");
        if (!Files.exists(snapshot, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(snapshot) || !Files.isRegularFile(snapshot, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidSnapshotException("unsafe candidate snapshot");
        }
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(snapshot)) {
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length > MAX_BYTES) throw new InvalidSnapshotException("candidate snapshot too large");
            JsonNode root = JSON.readTree(bytes);
            if (root == null || !root.isObject() || root.size() != 2
                || !root.path("schemaVersion").isInt() || root.path("schemaVersion").intValue() != 1
                || !root.path("candidates").isArray()) {
                throw new InvalidSnapshotException("invalid candidate snapshot");
            }
            List<SkillCandidate> result = new ArrayList<>();
            Map<String, Boolean> ids = new HashMap<>();
            for (JsonNode item : root.path("candidates")) {
                if (!item.isObject() || item.size() != FIELDS.size()) {
                    throw new InvalidSnapshotException("invalid candidate entry");
                }
                for (String field : FIELDS) if (!item.has(field) || !item.path(field).isTextual()) {
                    throw new InvalidSnapshotException("invalid candidate entry");
                }
                SkillCandidate candidate = new SkillCandidate(item.path("externalId").textValue(),
                    item.path("name").textValue(), item.path("source").textValue(),
                    item.path("sourceUrl").textValue(), item.path("contentHash").textValue(),
                    Instant.parse(item.path("observedAt").textValue()));
                if (ids.putIfAbsent(candidate.externalId(), true) != null) {
                    throw new InvalidSnapshotException("invalid candidate identity");
                }
                result.add(candidate);
            }
            return List.copyOf(result);
        } catch (IOException | DateTimeParseException | IllegalArgumentException ex) {
            throw new InvalidSnapshotException("candidate snapshot unreadable", ex);
        }
    }

    public static synchronized List<SkillCandidate> refresh(Path snapshot,
                                                              List<SkillCandidate> discovered) {
        if (discovered == null) throw new IllegalArgumentException("discovered candidates required");
        List<SkillCandidate> old = load(snapshot);
        List<SkillCandidate> changed = SkillsShCandidateDiff.needingReview(old, discovered);
        Map<String, SkillCandidate> merged = new HashMap<>();
        for (SkillCandidate item : old) merged.put(item.externalId(), item);
        Set<String> discoveredIds = new HashSet<>();
        for (SkillCandidate item : discovered) {
            if (item == null || !discoveredIds.add(item.externalId())) {
                throw new InvalidSnapshotException("duplicate discovered candidate");
            }
            merged.put(item.externalId(), item);
        }
        List<SkillCandidate> sorted = merged.values().stream()
            .sorted(Comparator.comparing(SkillCandidate::externalId)).toList();
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode array = root.putArray("candidates");
        for (SkillCandidate item : sorted) {
            ObjectNode node = array.addObject();
            node.put("externalId", item.externalId());
            node.put("name", item.name());
            node.put("source", item.source());
            node.put("sourceUrl", item.sourceUrl());
            node.put("contentHash", item.contentHash());
            node.put("observedAt", item.observedAt().toString());
        }
        Path temporary = null;
        try {
            Path target = snapshot.toAbsolutePath().normalize();
            Path parent = target.getParent();
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".skills-sh-", ".json");
            byte[] bytes = JSON.writeValueAsBytes(root);
            if (bytes.length > MAX_BYTES) throw new InvalidSnapshotException("candidate snapshot too large");
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
            return changed;
        } catch (IOException ex) {
            throw new InvalidSnapshotException("candidate snapshot write failed", ex);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* best effort */ }
            }
        }
    }

    public static final class InvalidSnapshotException extends RuntimeException {
        public InvalidSnapshotException(String message) { super(message); }
        public InvalidSnapshotException(String message, Throwable cause) { super(message, cause); }
    }
}
