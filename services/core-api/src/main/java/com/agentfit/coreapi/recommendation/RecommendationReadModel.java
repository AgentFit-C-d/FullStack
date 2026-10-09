package com.agentfit.coreapi.recommendation;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.util.HashSet;
import java.util.List;

/** Projects A's owner-checked latest recommendation without re-running AI or the planner. */
public record RecommendationReadModel(String recommendationId,
                                      RecommendationDecision.Status status,
                                      Validity validity,
                                      PreviewBasis basis,
                                      List<String> toolKeys) {
    public enum Validity { CURRENT, STALE }

    public RecommendationReadModel { toolKeys = List.copyOf(toolKeys); }

    public static RecommendationReadModel latest(StoredRecommendationState stored,
                                                 PreviewBasis currentBasis) {
        if (stored == null) return null;
        if (blank(stored.recommendationId()) || stored.status() == null
            || !validBasis(stored.basis()) || !validBasis(currentBasis)
            || !stored.basis().projectId().equals(currentBasis.projectId())
            || stored.toolKeys() == null) {
            throw new IllegalArgumentException("invalid stored recommendation state");
        }
        List<String> keys = stored.toolKeys();
        if (keys.stream().anyMatch(RecommendationReadModel::blank)
            || new HashSet<>(keys).size() != keys.size()
            || stored.status() == RecommendationDecision.Status.RECOMMENDED && keys.isEmpty()
            || stored.status() != RecommendationDecision.Status.RECOMMENDED && !keys.isEmpty()) {
            throw new IllegalArgumentException("invalid stored recommendation items");
        }
        return new RecommendationReadModel(stored.recommendationId(), stored.status(),
            stored.basis().equals(currentBasis) ? Validity.CURRENT : Validity.STALE,
            stored.basis(), keys);
    }

    private static boolean validBasis(PreviewBasis basis) {
        return basis != null && !blank(basis.projectId()) && basis.projectVersion() > 0
            && !blank(basis.confirmedProfileId()) && basis.confirmedProfileVersion() > 0
            && !blank(basis.confirmationEventId()) && basis.reviewVersion() >= 0
            && basis.developerVersion() >= 0 && basis.environmentVersion() >= 0
            && !blank(basis.catalogReleaseId()) && basis.catalogHash() != null
            && basis.catalogHash().matches("[0-9a-f]{64}");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
