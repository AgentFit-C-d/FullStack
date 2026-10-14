package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/** Rechecks stored approval and Preview metadata before invoking the ZIP byte writer. */
public final class ApprovedPreviewZipExporter {
    private ApprovedPreviewZipExporter() {}

    public static byte[] export(StoredPreviewState preview, StoredApprovalState approval,
                                PreviewBasis currentBasis, String requestedApprovalId,
                                String requestedPreviewId, String submittedFingerprint,
                                PreviewFingerprintInput regeneratedInput, Instant now) {
        if (preview == null || approval == null || now == null || regeneratedInput == null
            || blank(preview.previewId()) || blank(approval.approvalId())
            || blank(approval.previewId()) || blank(requestedApprovalId)
            || blank(requestedPreviewId) || approval.approvedAt() == null
            || approval.expiresAt() == null || preview.createdAt() == null
            || preview.expiresAt() == null
            || !hash(approval.fingerprint()) || !hash(preview.fingerprint())) {
            throw invalid("incomplete approval state");
        }
        if (!approval.approvalId().equals(requestedApprovalId)
            || !approval.previewId().equals(requestedPreviewId)
            || !approval.previewId().equals(preview.previewId())
            || !same(approval.fingerprint(), preview.fingerprint())) {
            throw invalid("approval does not match Preview");
        }
        if (approval.approvedAt().isBefore(preview.createdAt())
            || !approval.approvedAt().isBefore(approval.expiresAt())
            || approval.approvedAt().isAfter(now)
            || approval.expiresAt().isAfter(preview.expiresAt())) {
            throw invalid("invalid approval lifetime");
        }
        if (!now.isBefore(approval.expiresAt())) {
            throw new ExpiredApprovalException("approval expired");
        }

        PreviewFingerprintResult regenerated = PreviewFingerprint.compute(regeneratedInput);
        PreviewFreshnessGate.requireCurrent(preview, currentBasis, submittedFingerprint,
            regenerated, now);
        return PreviewZipExporter.export(regeneratedInput, preview.fingerprint());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean same(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
            right.getBytes(StandardCharsets.US_ASCII));
    }

    private static InvalidApprovalException invalid(String reason) {
        return new InvalidApprovalException(reason);
    }

    public static final class InvalidApprovalException extends IllegalArgumentException {
        public InvalidApprovalException(String message) { super(message); }
    }

    public static final class ExpiredApprovalException extends IllegalStateException {
        public ExpiredApprovalException(String message) { super(message); }
    }
}
