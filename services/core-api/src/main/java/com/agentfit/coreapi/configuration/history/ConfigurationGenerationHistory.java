package com.agentfit.coreapi.configuration.history;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Minimum metadata for one generated ZIP; never contains configuration bytes or Diff. */
public final class ConfigurationGenerationHistory {
    public enum Status { GENERATED }
    public enum VerificationStatus { NOT_RUN }
    public enum VerificationSource { NONE }
    public enum Validity { CURRENT, STALE }

    public record FileMetadata(String relativePath, PreviewFile.Action action, String afterHash) {}

    private final String id;
    private final String recommendationId;
    private final String previewId;
    private final String approvalId;
    private final PreviewBasis basis;
    private final String fingerprint;
    private final List<FileMetadata> files;
    private final Instant createdAt;

    private ConfigurationGenerationHistory(String id, String recommendationId, String previewId,
                                           String approvalId, PreviewBasis basis, String fingerprint,
                                           List<FileMetadata> files, Instant createdAt) {
        this.id = id;
        this.recommendationId = recommendationId;
        this.previewId = previewId;
        this.approvalId = approvalId;
        this.basis = basis;
        this.fingerprint = fingerprint;
        this.files = List.copyOf(files);
        this.createdAt = createdAt;
    }

    /** Call only after the approved ZIP was generated successfully; persistence belongs to A. */
    static ConfigurationGenerationHistory capture(String generationId, String previewId,
                                                  String approvalId, String expectedFingerprint,
                                                  PreviewFingerprintInput input, Clock serverClock) {
        if (!validId(generationId) || !validId(previewId) || !validId(approvalId)
            || expectedFingerprint == null || !expectedFingerprint.matches("[0-9a-f]{64}")
            || input == null || serverClock == null) {
            throw invalid();
        }
        PreviewFingerprintResult regenerated = PreviewFingerprint.compute(input);
        if (!MessageDigest.isEqual(expectedFingerprint.getBytes(StandardCharsets.US_ASCII),
            regenerated.fingerprint().getBytes(StandardCharsets.US_ASCII))) {
            throw invalid();
        }
        List<FileMetadata> files = regenerated.files().stream()
            .map(file -> new FileMetadata(file.relativePath(), file.action(), file.afterHash()))
            .toList();
        return new ConfigurationGenerationHistory(generationId, input.recommendationId(),
            previewId, approvalId, input.basis(), regenerated.fingerprint(), files,
            Instant.now(serverClock));
    }

    /** Rebuilds content-free history from B9/B8/B5/B1 rows after an owner-checked DB lookup. */
    public static ConfigurationGenerationHistory fromStoredMetadata(
        String generationId, String recommendationId, String previewId, String approvalId,
        PreviewBasis basis, String fingerprint, List<FileMetadata> files, Instant generatedAt) {
        if (!validId(generationId) || !validId(recommendationId) || !validId(previewId)
            || !validId(approvalId) || basis == null || !validId(basis.projectId())
            || basis.projectVersion() <= 0 || !validId(basis.confirmedProfileId())
            || basis.confirmedProfileVersion() <= 0 || !validId(basis.confirmationEventId())
            || basis.reviewVersion() < 0 || basis.developerVersion() < 0
            || basis.environmentVersion() < 0 || blank(basis.catalogReleaseId())
            || basis.catalogHash() == null || !basis.catalogHash().matches("[0-9a-f]{64}")
            || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")
            || files == null || files.isEmpty() || files.size() > 20 || generatedAt == null) {
            throw invalid();
        }
        Set<String> paths = new HashSet<>();
        for (FileMetadata file : files) {
            if (file == null || file.action() == null || file.afterHash() == null
                || !file.afterHash().matches("[0-9a-f]{64}") || !safePath(file.relativePath())
                || !paths.add(file.relativePath().toLowerCase(Locale.ROOT))) {
                throw invalid();
            }
        }
        for (String path : paths) {
            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
                if (paths.contains(path.substring(0, slash))) throw invalid();
            }
        }
        return new ConfigurationGenerationHistory(generationId, recommendationId, previewId,
            approvalId, basis, fingerprint, files, generatedAt);
    }

    public String id() { return id; }
    public String recommendationId() { return recommendationId; }
    public String previewId() { return previewId; }
    public String approvalId() { return approvalId; }
    public PreviewBasis basis() { return basis; }
    public String fingerprint() { return fingerprint; }
    public List<FileMetadata> files() { return files; }
    public Instant createdAt() { return createdAt; }
    public Status status() { return Status.GENERATED; }
    public VerificationStatus verificationStatus() { return VerificationStatus.NOT_RUN; }
    public VerificationSource verificationSource() { return VerificationSource.NONE; }

    public Validity validity(PreviewBasis currentBasis) {
        if (currentBasis == null || !basis.projectId().equals(currentBasis.projectId())) {
            throw invalid();
        }
        return basis.equals(currentBasis) ? Validity.CURRENT : Validity.STALE;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean validId(String value) {
        return !blank(value) && value.codePointCount(0, value.length()) <= 128;
    }

    private static boolean safePath(String path) {
        if (blank(path) || path.startsWith("/") || path.contains("\\") || path.contains(":")
            || path.codePointCount(0, path.length()) > 200) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".")
                || part.endsWith(" ") || part.chars().anyMatch(c -> c < 32 || c == 127
                    || "<>\"|?*".indexOf(c) >= 0)) return false;
            String stem = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4",
                    "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3",
                    "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9").contains(stem)) return false;
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid generation history input");
    }
}
