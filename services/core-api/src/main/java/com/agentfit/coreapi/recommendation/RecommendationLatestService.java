package com.agentfit.coreapi.recommendation;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.util.Objects;

/** B's latest-recommendation read boundary; no AI call or recommendation regeneration occurs. */
public final class RecommendationLatestService {
    /** A authenticates the caller, checks project ownership, and supplies current versions/Catalog. */
    public interface OwnerCheckedBasisReader {
        PreviewBasis load(String projectId);
    }

    /** A reads the latest persisted recommendation within the same owner-checked project. */
    public interface StoredRecommendationReader {
        StoredRecommendationState latest(String projectId);
    }

    private final OwnerCheckedBasisReader currentBasisReader;
    private final StoredRecommendationReader storedReader;

    public RecommendationLatestService(OwnerCheckedBasisReader currentBasisReader,
                                       StoredRecommendationReader storedReader) {
        this.currentBasisReader = Objects.requireNonNull(currentBasisReader);
        this.storedReader = Objects.requireNonNull(storedReader);
    }

    public RecommendationReadModel latest(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("project ID required");
        }
        PreviewBasis current = Objects.requireNonNull(currentBasisReader.load(projectId));
        if (!projectId.equals(current.projectId())) {
            throw new IllegalArgumentException("current basis belongs to another project");
        }
        return RecommendationReadModel.latest(storedReader.latest(projectId), current);
    }
}
