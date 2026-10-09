package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.PreviewApprovalIssuer;
import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.nio.file.Path;
import java.time.Clock;

/** Recomputes an approval candidate from reviewed Catalog bytes, never from a supplied Preview result. */
public final class CatalogPreviewApprovalWorkflow {
    private CatalogPreviewApprovalWorkflow() {}

    public static StoredApprovalState issue(Path releaseDirectory, String approvedCatalogHash,
                                            StoredRecommendationState storedRecommendation,
                                            PreviewBasis currentBasis, EnvironmentTarget currentTarget,
                                            CatalogPreviewRequest request, StoredPreviewState preview,
                                            String submittedFingerprint, boolean confirmation,
                                            String serverApprovalId, Clock serverClock) {
        PreviewFingerprintResult regenerated = CatalogPreviewAssembler.assemble(releaseDirectory,
            approvedCatalogHash, storedRecommendation, currentBasis, currentTarget, request);
        return PreviewApprovalIssuer.issue(preview, currentBasis, submittedFingerprint,
            regenerated, confirmation, serverApprovalId, serverClock);
    }
}
