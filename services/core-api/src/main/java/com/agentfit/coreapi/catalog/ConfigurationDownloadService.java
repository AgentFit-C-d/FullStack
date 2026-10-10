package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.history.ApprovedConfigurationGenerator;
import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

/** ZIP bytes leave B only after A atomically saves content-free generation history. */
public final class ConfigurationDownloadService {
    /** A authenticates, checks project ownership, and loads current records consistently. */
    public interface TrustedDownloadReader {
        TrustedContext load(String projectId, String approvalId);
    }

    /** A rechecks current basis/approval before committing only history metadata. */
    public interface HistoryStore {
        String saveIfCurrent(TrustedContext context, ConfigurationGenerationHistory history);
    }

    public record TrustedContext(PreviewBasis currentBasis, EnvironmentTarget currentTarget,
                                 StoredRecommendationState recommendation,
                                 StoredPreviewState preview, StoredApprovalState approval) {}

    private final TrustedDownloadReader downloads;
    private final RecommendationCreationService.ApprovedCatalogReader catalogs;
    private final Supplier<String> serverGenerationIds;
    private final Clock serverClock;
    private final HistoryStore historyStore;

    public ConfigurationDownloadService(TrustedDownloadReader downloads,
                                        RecommendationCreationService.ApprovedCatalogReader catalogs,
                                        Supplier<String> serverGenerationIds, Clock serverClock,
                                        HistoryStore historyStore) {
        this.downloads = Objects.requireNonNull(downloads);
        this.catalogs = Objects.requireNonNull(catalogs);
        this.serverGenerationIds = Objects.requireNonNull(serverGenerationIds);
        this.serverClock = Objects.requireNonNull(serverClock);
        this.historyStore = Objects.requireNonNull(historyStore);
    }

    public ApprovedConfigurationGenerator.Generated download(
        String projectId, String requestedApprovalId, String requestedPreviewId,
        String submittedFingerprint, CatalogPreviewRequest request) {
        if (blank(projectId) || blank(requestedApprovalId) || blank(requestedPreviewId)
            || request == null) {
            throw new IllegalArgumentException("incomplete download request");
        }
        TrustedContext context = Objects.requireNonNull(downloads.load(projectId, requestedApprovalId));
        if (context.currentBasis() == null
            || !projectId.equals(context.currentBasis().projectId())) {
            throw new IllegalArgumentException("download context belongs to another project");
        }
        var catalog = Objects.requireNonNull(catalogs.current());
        ApprovedConfigurationGenerator.Generated generated = CatalogApprovedConfigurationWorkflow.generate(
            catalog.directory(), catalog.approvedHash(), context.recommendation(),
            context.currentBasis(), context.currentTarget(), request, context.preview(),
            context.approval(), requestedApprovalId, requestedPreviewId, submittedFingerprint,
            serverGenerationIds.get(), serverClock);
        String savedId = historyStore.saveIfCurrent(context, generated.history());
        if (!generated.history().id().equals(savedId)) {
            throw new IllegalStateException("generation history was not saved correctly");
        }
        return generated;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
