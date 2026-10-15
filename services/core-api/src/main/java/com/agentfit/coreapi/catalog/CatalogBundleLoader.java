package com.agentfit.coreapi.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Verifies a local, immutable release envelope before any Catalog schema is parsed. */
public final class CatalogBundleLoader {
    private static final ObjectMapper JSON = new ObjectMapper(
        new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION))
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> REQUIRED = Set.of("capabilities.json", "tools.json",
        "support-matrix.json", "relations.json", "permissions.json", "client-capabilities.json");
    private static final Set<String> MANIFEST_FIELDS = Set.of("schemaVersion", "releaseId", "catalogHash", "files");
    private static final Set<String> FILE_FIELDS = Set.of("path", "sha256");
    private static final int MAX_FILES = 100;
    private static final long MAX_FILE_BYTES = 1_048_576;
    private static final long MAX_TOTAL_BYTES = 8_388_608;
    private static final long MAX_MANIFEST_BYTES = 65_536;

    private CatalogBundleLoader() {}

    public static VerifiedCatalogBundle load(Path directory) {
        try {
            Path root = directory.toAbsolutePath().normalize();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                throw unavailable("release directory unavailable");
            }
            byte[] manifestBytes = read(root.resolve("manifest.json"), MAX_MANIFEST_BYTES);
            JsonNode manifest = JSON.readTree(strictText(manifestBytes));
            if (manifest == null || !manifest.isObject() || !fields(manifest).equals(MANIFEST_FIELDS)
                || !manifest.path("schemaVersion").isInt()
                || manifest.path("schemaVersion").intValue() != 1) {
                throw unavailable("unsupported manifest schema");
            }
            String releaseId = requiredText(manifest, "releaseId");
            if (!releaseId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
                throw unavailable("invalid release ID");
            }
            String expectedCatalogHash = requiredHash(manifest, "catalogHash");
            JsonNode entries = manifest.path("files");
            if (!entries.isArray() || entries.size() < REQUIRED.size() || entries.size() > MAX_FILES) {
                throw unavailable("invalid file list");
            }

            Map<String, String> hashes = new HashMap<>();
            Map<String, String> texts = new HashMap<>();
            Set<String> caseFoldedPaths = new HashSet<>();
            long totalBytes = 0;
            for (JsonNode entry : entries) {
                if (!entry.isObject() || !fields(entry).equals(FILE_FIELDS)) {
                    throw unavailable("invalid file entry");
                }
                String relative = requiredText(entry, "path");
                validatePath(relative);
                if (!caseFoldedPaths.add(relative.toLowerCase(Locale.ROOT))) {
                    throw unavailable("duplicate file path");
                }
                String expectedFileHash = requiredHash(entry, "sha256");
                Path file = root.resolve(relative).normalize();
                if (!file.startsWith(root) || hasSymbolicPart(root, file)) {
                    throw unavailable("unsafe file path or symlink");
                }
                byte[] bytes = read(file, MAX_FILE_BYTES);
                totalBytes += bytes.length;
                if (totalBytes > MAX_TOTAL_BYTES || !sha256(bytes).equals(expectedFileHash)) {
                    throw unavailable("file size/hash mismatch");
                }
                hashes.put(relative, expectedFileHash);
                texts.put(relative, strictText(bytes));
            }
            if (!hashes.keySet().containsAll(REQUIRED)) {
                throw unavailable("missing required catalog file");
            }
            rejectUnlistedFiles(root, hashes.keySet());
            String actualCatalogHash = releaseHash(releaseId, hashes);
            if (!actualCatalogHash.equals(expectedCatalogHash)) {
                throw unavailable("release hash mismatch");
            }
            return new VerifiedCatalogBundle(releaseId, actualCatalogHash, texts);
        } catch (CatalogUnavailableException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new CatalogUnavailableException("catalog release unavailable", exception);
        }
    }

    private static byte[] read(Path path, long maximum) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            || Files.size(path) > maximum) {
            throw unavailable("catalog file unavailable or oversized");
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > maximum) throw unavailable("catalog file oversized");
        return bytes;
    }

    private static String strictText(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw unavailable("invalid manifest field: " + field);
        }
        return value.textValue();
    }

    private static String requiredHash(JsonNode node, String field) {
        String hash = requiredText(node, field);
        if (!hash.matches("[0-9a-f]{64}")) throw unavailable("invalid SHA-256 hash");
        return hash;
    }

    private static void validatePath(String path) {
        if (!path.matches("[A-Za-z0-9._/-]+") || path.startsWith("/")
            || path.contains("//") || path.endsWith("/") || path.equals("manifest.json")) {
            throw unavailable("unsafe catalog path");
        }
        for (String part : path.split("/")) {
            if (part.equals(".") || part.equals("..")) throw unavailable("unsafe catalog path");
        }
        if (!REQUIRED.contains(path)
            && !(path.startsWith("templates/") || path.startsWith("guides/"))) {
            throw unavailable("unrecognized catalog path");
        }
    }

    private static boolean hasSymbolicPart(Path root, Path file) {
        Path current = root;
        for (Path part : root.relativize(file)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) return true;
        }
        return false;
    }

    private static void rejectUnlistedFiles(Path root, Set<String> listed) throws IOException {
        Set<String> expected = new HashSet<>(listed);
        expected.add("manifest.json");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                if (path.equals(root)) continue;
                if (Files.isSymbolicLink(path)) throw unavailable("catalog symlink present");
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || !expected.contains(relative)) throw unavailable("unlisted catalog file");
            }
        }
    }

    private static String releaseHash(String releaseId, Map<String, String> hashes) {
        List<String> paths = new ArrayList<>(hashes.keySet());
        paths.sort(String::compareTo);
        StringBuilder preimage = new StringBuilder("agentfit-catalog-v1\n").append(releaseId).append('\n');
        for (String path : paths) preimage.append(path).append('\t').append(hashes.get(path)).append('\n');
        return sha256(preimage.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static CatalogUnavailableException unavailable(String reason) {
        return new CatalogUnavailableException(reason);
    }

    public static final class CatalogUnavailableException extends IllegalStateException {
        public CatalogUnavailableException(String message) { super(message); }
        public CatalogUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
