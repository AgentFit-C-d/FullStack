package com.agentfit.coreapi.recommendation.selection;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.RecommendationDecision.Status;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks a trusted recommendation snapshot before any selected tool reaches Preview. */
public final class RecommendationPreviewGate {
    private RecommendationPreviewGate() {}

    public static void requireEligible(StoredRecommendationState stored, PreviewBasis currentBasis,
                                       String requestedRecommendationId, PreviewBasis requestedBasis,
                                       List<String> selectedToolKeys) {
        if (stored == null || blank(stored.recommendationId()) || stored.basis() == null
            || currentBasis == null || requestedBasis == null
            || blank(requestedRecommendationId) || stored.status() == null
            || stored.toolKeys() == null || stored.toolKeys().isEmpty()
            || selectedToolKeys == null || selectedToolKeys.isEmpty()) {
            throw invalid();
        }
        if (!stored.recommendationId().equals(requestedRecommendationId)
            || stored.status() != Status.RECOMMENDED) {
            throw invalid();
        }
        if (!stored.basis().equals(currentBasis) || !requestedBasis.equals(currentBasis)) {
            throw new StaleRecommendationException("recommendation basis changed");
        }
        Set<String> offered = new HashSet<>();
        for (String key : stored.toolKeys()) {
            if (blank(key) || !offered.add(key)) throw invalid();
        }
        Set<String> selected = new HashSet<>();
        for (String key : selectedToolKeys) {
            if (blank(key) || !selected.add(key) || !offered.contains(key)) throw invalid();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static InvalidRecommendationException invalid() {
        return new InvalidRecommendationException("recommendation is not eligible for Preview");
    }

    public static final class InvalidRecommendationException extends IllegalArgumentException {
        public InvalidRecommendationException(String message) { super(message); }
    }

    public static final class StaleRecommendationException extends IllegalStateException {
        public StaleRecommendationException(String message) { super(message); }
    }
}
