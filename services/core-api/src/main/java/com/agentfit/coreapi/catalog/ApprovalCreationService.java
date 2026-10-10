package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/** B's final-confirmation boundary; an approval is returned only after A stores it. */
public final class ApprovalCreationService {
    /** A authenticates and loads a persisted READY Preview with a current owner-checked basis. */
    public interface TrustedPreviewReader {
        TrustedContext load(String projectId, String previewId);
    }

    /** A rechecks the Preview, current basis, and expiry before an atomic approval/audit save. */
    public interface ApprovalStore {
        StoredApprovalState saveIfCurrent(TrustedContext context, StoredApprovalState issued);
    }

    public record TrustedContext(PreviewBasis currentBasis, StoredPreviewState readyPreview) {}

    private final TrustedPreviewReader previews;
    private final RecommendationCreationService.ApprovedCatalogReader catalogs;
    private final Supplier<String> serverApprovalIds;
    private final Clock serverClock;
    private final ApprovalStore store;

    public ApprovalCreationService(TrustedPreviewReader previews,
                                   RecommendationCreationService.ApprovedCatalogReader catalogs,
                                   Supplier<String> serverApprovalIds, Clock serverClock,
                                   ApprovalStore store) {
        this.previews = Objects.requireNonNull(previews);
        this.catalogs = Objects.requireNonNull(catalogs);
        this.serverApprovalIds = Objects.requireNonNull(serverApprovalIds);
        this.serverClock = Objects.requireNonNull(serverClock);
        this.store = Objects.requireNonNull(store);
    }

    public StoredApprovalState approve(String projectId, String previewId,
                                       String submittedFingerprint, boolean confirmation) {
        if (projectId == null || projectId.isBlank() || previewId == null || previewId.isBlank()) {
            throw new IllegalArgumentException("project and Preview required");
        }
        TrustedContext context = Objects.requireNonNull(previews.load(projectId, previewId));
        if (context.currentBasis() == null
            || !projectId.equals(context.currentBasis().projectId())) {
            throw new IllegalArgumentException("Preview context belongs to another project");
        }
        var catalog = Objects.requireNonNull(catalogs.current());
        StoredApprovalState issued = CatalogReadyPreviewApprovalWorkflow.issue(
            catalog.directory(), catalog.approvedHash(), context.readyPreview(),
            context.currentBasis(), previewId, submittedFingerprint, confirmation,
            serverApprovalIds.get(), serverClock);
        StoredApprovalState saved = store.saveIfCurrent(context, issued);
        if (!issued.equals(saved)) {
            throw new IllegalStateException("approval metadata was not saved correctly");
        }
        return saved;
    }
}
