package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import java.time.Clock;
import java.time.Instant;

/** Creates minimal approval metadata from A's owner-checked READY Preview. */
public final class PreviewApprovalIssuer {
    private PreviewApprovalIssuer() {}

    /** For the draft approval API: A must supply an owner-checked, persisted READY Preview. */
    public static StoredApprovalState issueStoredReady(StoredPreviewState readyPreview,
                                                       PreviewBasis currentBasis,
                                                       String requestedPreviewId,
                                                       String submittedFingerprint,
                                                       boolean confirmation,
                                                       String serverApprovalId,
                                                       Clock serverClock) {
        if (serverClock == null || serverApprovalId == null || serverApprovalId.isBlank()
            || serverApprovalId.codePointCount(0, serverApprovalId.length()) > 128) {
            throw new IllegalArgumentException("invalid approval ID or clock");
        }
        Instant now = Instant.now(serverClock);
        PreviewFreshnessGate.requireStoredCurrent(readyPreview, currentBasis,
            submittedFingerprint, now);
        if (!readyPreview.previewId().equals(requestedPreviewId)) {
            throw new PreviewFreshnessGate.InvalidPreviewStateException("Preview ID differs");
        }
        if (!confirmation) {
            throw new PreviewFreshnessGate.InvalidPreviewStateException("explicit confirmation required");
        }
        return new StoredApprovalState(serverApprovalId, readyPreview.previewId(),
            readyPreview.fingerprint(), now, readyPreview.expiresAt());
    }
}
