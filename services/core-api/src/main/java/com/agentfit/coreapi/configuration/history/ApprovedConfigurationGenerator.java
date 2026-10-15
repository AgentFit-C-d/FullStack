package com.agentfit.coreapi.configuration.history;

import com.agentfit.coreapi.configuration.export.ApprovedPreviewZipExporter;
import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Returns history metadata only after approval checks and ZIP generation succeed. */
public final class ApprovedConfigurationGenerator {
    private ApprovedConfigurationGenerator() {}

    public record Generated(byte[] zipBytes, ConfigurationGenerationHistory history) {
        public Generated {
            if (zipBytes == null || history == null) {
                throw new IllegalArgumentException("incomplete generated configuration");
            }
            zipBytes = zipBytes.clone();
        }

        @Override
        public byte[] zipBytes() {
            return zipBytes.clone();
        }
    }

    public static Generated generate(StoredPreviewState preview, StoredApprovalState approval,
                                     PreviewBasis currentBasis, String requestedApprovalId,
                                     String requestedPreviewId, String submittedFingerprint,
                                     PreviewFingerprintInput regeneratedInput, String generationId,
                                     Clock serverClock) {
        if (serverClock == null || generationId == null || generationId.isBlank()) {
            throw new IllegalArgumentException("invalid generation request");
        }
        Instant now = Instant.now(serverClock);
        byte[] zip = ApprovedPreviewZipExporter.export(preview, approval, currentBasis,
            requestedApprovalId, requestedPreviewId, submittedFingerprint, regeneratedInput, now);
        ConfigurationGenerationHistory history = ConfigurationGenerationHistory.capture(
            generationId, requestedPreviewId, requestedApprovalId, submittedFingerprint,
            regeneratedInput, Clock.fixed(now, ZoneOffset.UTC));
        return new Generated(zip, history);
    }
}
