package com.agentfit.coreapi.configuration.preview;

import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical, server-side hash of a regenerated Preview; no persistence or approval. */
public final class PreviewFingerprint {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_TOOLS = 20;
    private static final int MAX_POLICIES = 200;
    private static final int MAX_FILES = 20;
    private static final int MAX_CODE_POINTS_PER_FILE = 100_000;
    private static final long MAX_CONTENT_BYTES = 1_048_576;

    private PreviewFingerprint() {}

    public static PreviewFingerprintResult compute(PreviewFingerprintInput input) {
        if (input == null || input.basis() == null || blank(input.recommendationId())
            || blank(input.generatorVersion()) || input.selectedToolIds() == null
            || input.selectedToolIds().isEmpty() || input.policies() == null
            || input.generatedFiles() == null || input.generatedFiles().isEmpty()) {
            throw invalid("incomplete Preview fingerprint input");
        }
        validateBudget(input);
        validateBasis(input.basis());

        List<String> selected = new ArrayList<>();
        Set<String> uniqueTools = new HashSet<>();
        for (String key : input.selectedToolIds()) {
            if (blank(key) || !uniqueTools.add(key)) throw invalid("invalid or duplicate selected tool");
            selected.add(key);
        }
        selected.sort(String::compareTo);

        List<PermissionSelection> policies = new ArrayList<>();
        Set<String> uniqueMappings = new HashSet<>();
        for (PermissionSelection policy : input.policies()) {
            if (policy == null || blank(policy.toolKey()) || blank(policy.mappingKey())
                || policy.policy() == null || !uniqueTools.contains(policy.toolKey())
                || !uniqueMappings.add(policy.toolKey() + "\u0000" + policy.mappingKey())) {
                throw invalid("invalid or duplicate policy choice");
            }
            policies.add(policy);
        }
        policies.sort(Comparator.comparing(PermissionSelection::toolKey)
            .thenComparing(PermissionSelection::mappingKey));

        List<PreviewFile> files = PreviewFileComparator.compare(input.generatedFiles(),
            input.existingState(), input.providedFiles());
        if (files.isEmpty()) throw invalid("empty generated file set");

        Map<String, Object> selection = ordered();
        selection.put("basis", basisFields(input.basis()));
        selection.put("recommendationId", input.recommendationId());
        selection.put("selectedToolIds", selected);
        List<Map<String, Object>> policyRows = new ArrayList<>();
        for (PermissionSelection policy : policies) {
            Map<String, Object> row = ordered();
            row.put("toolKey", policy.toolKey());
            row.put("mappingKey", policy.mappingKey());
            row.put("policy", policy.policy().name());
            policyRows.add(row);
        }
        selection.put("policies", policyRows);
        String selectionDigest = digest(selection);

        List<Map<String, Object>> fileRows = new ArrayList<>();
        for (PreviewFile file : files) {
            Map<String, Object> row = ordered();
            row.put("targetKey", file.targetKey());
            row.put("relativePath", file.relativePath());
            row.put("action", file.action().name());
            row.put("beforeHash", file.beforeHash());
            row.put("afterHash", file.afterHash());
            fileRows.add(row);
        }
        Map<String, Object> content = ordered();
        content.put("files", fileRows);
        String contentDigest = digest(content);

        Map<String, Object> approvalTarget = ordered();
        approvalTarget.put("selectionDigest", selectionDigest);
        approvalTarget.put("contentDigest", contentDigest);
        approvalTarget.put("generatorVersion", input.generatorVersion());
        return new PreviewFingerprintResult(files, selectionDigest, contentDigest,
            digest(approvalTarget));
    }

    private static Map<String, Object> basisFields(PreviewBasis basis) {
        Map<String, Object> values = ordered();
        values.put("projectId", basis.projectId());
        values.put("projectVersion", basis.projectVersion());
        values.put("confirmedProfileId", basis.confirmedProfileId());
        values.put("confirmedProfileVersion", basis.confirmedProfileVersion());
        values.put("confirmationEventId", basis.confirmationEventId());
        values.put("reviewVersion", basis.reviewVersion());
        values.put("developerVersion", basis.developerVersion());
        values.put("environmentVersion", basis.environmentVersion());
        values.put("catalogReleaseId", basis.catalogReleaseId());
        values.put("catalogHash", basis.catalogHash());
        return values;
    }

    private static void validateBasis(PreviewBasis basis) {
        if (blank(basis.projectId()) || basis.projectVersion() <= 0
            || blank(basis.confirmedProfileId()) || basis.confirmedProfileVersion() <= 0
            || blank(basis.confirmationEventId()) || basis.reviewVersion() < 0
            || basis.developerVersion() < 0 || basis.environmentVersion() < 0
            || blank(basis.catalogReleaseId()) || basis.catalogHash() == null
            || !basis.catalogHash().matches("[0-9a-f]{64}")) {
            throw invalid("incomplete or invalid Preview basis");
        }
    }

    private static void validateBudget(PreviewFingerprintInput input) {
        if (input.selectedToolIds().size() > MAX_TOOLS
            || input.policies().size() > MAX_POLICIES
            || input.providedFiles() == null) {
            throw new LimitExceededException("Preview input exceeds budget");
        }
        validateFiles(input.generatedFiles());
        validateFiles(input.providedFiles());
    }

    private static void validateFiles(List<PreviewInputFile> files) {
        if (files.size() > MAX_FILES) throw new LimitExceededException("too many Preview files");
        long bytes = 0;
        for (PreviewInputFile file : files) {
            if (file == null || file.content() == null) {
                throw new LimitExceededException("invalid Preview file");
            }
            String content = file.content();
            if (content.codePointCount(0, content.length()) > MAX_CODE_POINTS_PER_FILE) {
                throw new LimitExceededException("Preview file too long");
            }
            try {
                bytes += StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(content)).remaining();
            } catch (CharacterCodingException exception) {
                throw new LimitExceededException("invalid Preview text");
            }
            if (bytes > MAX_CONTENT_BYTES) {
                throw new LimitExceededException("Preview content too large");
            }
        }
    }

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }

    private static String digest(Map<String, Object> value) {
        try {
            byte[] bytes = JSON.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("canonical JSON failed", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static InvalidFingerprintInputException invalid(String reason) {
        return new InvalidFingerprintInputException(reason);
    }

    public static final class InvalidFingerprintInputException extends IllegalArgumentException {
        public InvalidFingerprintInputException(String message) { super(message); }
    }

    public static final class LimitExceededException extends IllegalArgumentException {
        public LimitExceededException(String message) { super(message); }
    }
}
