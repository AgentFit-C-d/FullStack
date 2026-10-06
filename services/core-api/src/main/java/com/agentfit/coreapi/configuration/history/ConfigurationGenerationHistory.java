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
import java.util.List;

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
        if (blank(generationId) || blank(previewId) || blank(approvalId)
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
        if (currentBasis == null) throw invalid();
        return basis.equals(currentBasis) ? Validity.CURRENT : Validity.STALE;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid generation history input");
    }
}
