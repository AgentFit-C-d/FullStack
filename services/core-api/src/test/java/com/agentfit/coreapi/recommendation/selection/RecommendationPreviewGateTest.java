package com.agentfit.coreapi.recommendation.selection;

import static com.agentfit.coreapi.recommendation.RecommendationDecision.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationPreviewGateTest {
    private static final PreviewBasis BASIS = new PreviewBasis("project-1", 2,
        "profile-1", 3, "event-1", 4, 5, 6, "catalog-1", "a".repeat(64));

    private StoredRecommendationState recommendation(List<String> keys) {
        return new StoredRecommendationState("rec-1", BASIS, RECOMMENDED, keys);
    }

    @Test
    void acceptsOnlyToolsIncludedInCurrentRecommendedResult() {
        assertDoesNotThrow(() -> RecommendationPreviewGate.requireEligible(
            recommendation(List.of("tool-a", "tool-b")), BASIS, "rec-1", BASIS, List.of("tool-b")));
    }

    @Test
    void rejectsOtherRecommendationAndStaleOrSubstitutedBasis() {
        assertThrows(RecommendationPreviewGate.InvalidRecommendationException.class,
            () -> RecommendationPreviewGate.requireEligible(recommendation(List.of("tool-a")),
                BASIS, "rec-other", BASIS, List.of("tool-a")));
        PreviewBasis changed = new PreviewBasis("project-1", 2, "profile-1", 3,
            "event-1", 4, 5, 7, "catalog-1", "a".repeat(64));
        assertThrows(RecommendationPreviewGate.StaleRecommendationException.class,
            () -> RecommendationPreviewGate.requireEligible(recommendation(List.of("tool-a")),
                changed, "rec-1", changed, List.of("tool-a")));
        assertThrows(RecommendationPreviewGate.StaleRecommendationException.class,
            () -> RecommendationPreviewGate.requireEligible(recommendation(List.of("tool-a")),
                BASIS, "rec-1", changed, List.of("tool-a")));
    }

    @Test
    void rejectsUnreadyResultsAndToolsOutsideRecommendation() {
        for (var status : List.of(NO_ADDITIONS_NEEDED, NEEDS_INFORMATION, NO_COMPATIBLE_TOOLS)) {
            var stored = new StoredRecommendationState("rec-1", BASIS, status, List.of("tool-a"));
            assertThrows(RecommendationPreviewGate.InvalidRecommendationException.class,
                () -> RecommendationPreviewGate.requireEligible(stored, BASIS,
                    "rec-1", BASIS, List.of("tool-a")));
        }
        assertThrows(RecommendationPreviewGate.InvalidRecommendationException.class,
            () -> RecommendationPreviewGate.requireEligible(recommendation(List.of("tool-a")),
                BASIS, "rec-1", BASIS, List.of("tool-b")));
    }

    @Test
    void rejectsIncompleteOrDuplicateStoredAndSelectedTools() {
        assertThrows(RecommendationPreviewGate.InvalidRecommendationException.class,
            () -> RecommendationPreviewGate.requireEligible(null, BASIS, "rec-1", BASIS, List.of("tool-a")));
        assertThrows(RecommendationPreviewGate.InvalidRecommendationException.class,
            () -> RecommendationPreviewGate.requireEligible(recommendation(List.of("tool-a", "tool-a")),
                BASIS, "rec-1", BASIS, List.of("tool-a")));
        assertThrows(RecommendationPreviewGate.InvalidRecommendationException.class,
            () -> RecommendationPreviewGate.requireEligible(recommendation(Arrays.asList("tool-a", null)),
                BASIS, "rec-1", BASIS, List.of("tool-a")));
        assertThrows(RecommendationPreviewGate.InvalidRecommendationException.class,
            () -> RecommendationPreviewGate.requireEligible(recommendation(List.of("tool-a")),
                BASIS, "rec-1", BASIS, List.of("tool-a", "tool-a")));
        assertThrows(RecommendationPreviewGate.InvalidRecommendationException.class,
            () -> RecommendationPreviewGate.requireEligible(recommendation(List.of("tool-a")),
                BASIS, "rec-1", BASIS, List.of()));
    }
}
