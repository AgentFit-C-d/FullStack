package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import java.time.Clock;
import java.time.Instant;

/** Creates minimal approval metadata after B's freshness and explicit-confirmation checks. */
public final class PreviewApprovalIssuer {
    private PreviewApprovalIssuer() {}

    public static StoredApprovalState issue(StoredPreviewState preview, PreviewBasis currentBasis,
                                            String submittedFingerprint,
                                            PreviewFingerprintResult regenerated,
                                            boolean confirmation, String serverApprovalId,
                                            Clock serverClock) {
        if (serverClock == null || serverApprovalId == null || serverApprovalId.isBlank()
            || serverApprovalId.codePointCount(0, serverApprovalId.length()) > 128) {
            throw new IllegalArgumentException("invalid approval ID or clock");
        }
        Instant now = Instant.now(serverClock);
        PreviewFreshnessGate.requireApprovalEligible(preview, currentBasis,
            submittedFingerprint, regenerated, now, confirmation);
        return new StoredApprovalState(serverApprovalId, preview.previewId(),
            preview.fingerprint(), now, preview.expiresAt());
    }
}
