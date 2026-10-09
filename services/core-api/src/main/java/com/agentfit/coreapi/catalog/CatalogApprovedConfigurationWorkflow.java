package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.history.ApprovedConfigurationGenerator;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.nio.file.Path;
import java.time.Clock;

/** B-only export path. A must provide owner-checked stored records and persist returned history. */
public final class CatalogApprovedConfigurationWorkflow {
    private CatalogApprovedConfigurationWorkflow() {}

    public static ApprovedConfigurationGenerator.Generated generate(
        Path releaseDirectory, String approvedCatalogHash,
        StoredRecommendationState storedRecommendation, PreviewBasis currentBasis,
        EnvironmentTarget currentTarget, CatalogPreviewRequest request,
        StoredPreviewState preview, StoredApprovalState approval,
        String requestedApprovalId, String generationId, Clock serverClock) {
        PreviewFingerprintInput regenerated = CatalogPreviewAssembler.prepare(releaseDirectory,
            approvedCatalogHash, storedRecommendation, currentBasis, currentTarget, request);
        return ApprovedConfigurationGenerator.generate(preview, approval, currentBasis,
            requestedApprovalId, preview.previewId(), preview.fingerprint(),
            regenerated, generationId, serverClock);
    }
}
