package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/** Checks transient Preview freshness; access control and approval persistence belong to the caller. */
public final class PreviewFreshnessGate {
    private PreviewFreshnessGate() {}

    public static void requireApprovalEligible(StoredPreviewState stored, PreviewBasis currentBasis,
                                               String submittedFingerprint,
                                               PreviewFingerprintResult regenerated, Instant now,
                                               boolean confirmation) {
        requireCurrent(stored, currentBasis, submittedFingerprint, regenerated, now);
        if (!confirmation) throw new InvalidPreviewStateException("explicit confirmation required");
    }

    public static void requireCurrent(StoredPreviewState stored, PreviewBasis currentBasis,
                                      String submittedFingerprint,
                                      PreviewFingerprintResult regenerated, Instant now) {
        if (stored == null || blank(stored.previewId()) || stored.basis() == null
            || currentBasis == null || stored.expiresAt() == null || now == null
            || regenerated == null || !hash(stored.fingerprint())
            || !hash(submittedFingerprint) || !hash(regenerated.fingerprint())) {
            throw new InvalidPreviewStateException("incomplete Preview state");
        }
        if (!stored.basis().equals(currentBasis)) {
            throw new StalePreviewException("Preview basis changed");
        }
        if (!now.isBefore(stored.expiresAt())) {
            throw new ExpiredPreviewException("Preview expired");
        }
        if (!same(stored.fingerprint(), submittedFingerprint)
            || !same(stored.fingerprint(), regenerated.fingerprint())) {
            throw new StalePreviewException("Preview fingerprint changed");
        }
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean same(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
            right.getBytes(StandardCharsets.US_ASCII));
    }

    public static final class InvalidPreviewStateException extends IllegalArgumentException {
        public InvalidPreviewStateException(String message) { super(message); }
    }

    public static final class StalePreviewException extends IllegalStateException {
        public StalePreviewException(String message) { super(message); }
    }

    public static final class ExpiredPreviewException extends IllegalStateException {
        public ExpiredPreviewException(String message) { super(message); }
    }
}
