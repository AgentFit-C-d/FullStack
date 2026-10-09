package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.PreviewApprovalIssuer;
import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;

/** Final confirmation using the approved Catalog and a persisted READY Preview, without file content. */
public final class CatalogReadyPreviewApprovalWorkflow {
    private CatalogReadyPreviewApprovalWorkflow() {}

    public static StoredApprovalState issue(Path releaseDirectory, String approvedCatalogHash,
                                            StoredPreviewState readyPreview, PreviewBasis currentBasis,
                                            String submittedFingerprint, boolean confirmation,
                                            String serverApprovalId, Clock serverClock) {
        VerifiedCatalogBundle bundle = CatalogBundleLoader.load(releaseDirectory);
        if (approvedCatalogHash == null || !approvedCatalogHash.matches("[0-9a-f]{64}")
            || !MessageDigest.isEqual(bundle.catalogHash().getBytes(StandardCharsets.US_ASCII),
                approvedCatalogHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new CatalogBundleLoader.CatalogUnavailableException("catalog hash is not approved");
        }
        if (currentBasis == null || !bundle.releaseId().equals(currentBasis.catalogReleaseId())
            || !bundle.catalogHash().equals(currentBasis.catalogHash())) {
            throw new CatalogBundleLoader.CatalogUnavailableException("current Catalog basis differs");
        }
        return PreviewApprovalIssuer.issueStoredReady(readyPreview, currentBasis,
            submittedFingerprint, confirmation, serverApprovalId, serverClock);
    }
}
