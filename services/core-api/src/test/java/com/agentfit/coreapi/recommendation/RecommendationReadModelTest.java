package com.agentfit.coreapi.recommendation;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationReadModelTest {
    private static final PreviewBasis BASIS = new PreviewBasis("project-1", 2,
        "profile-1", 3, "event-1", 4, 5, 6, "catalog-1", "a".repeat(64));

    @Test
    void latestReadReturnsNullOnlyWhenNoStoredRecommendationExists() {
        assertNull(RecommendationReadModel.latest(null, BASIS));
        assertThrows(IllegalArgumentException.class,
            () -> RecommendationReadModel.latest(null, null));
        var stored = new StoredRecommendationState("rec-1", BASIS,
            RecommendationDecision.Status.RECOMMENDED, List.of("tool-a"));
        var result = RecommendationReadModel.latest(stored, BASIS);
        assertEquals(RecommendationReadModel.Validity.CURRENT, result.validity());
        assertEquals("rec-1", result.recommendationId());
        assertEquals(List.of("tool-a"), result.toolKeys());
    }

    @Test
    void anyProfileEnvironmentOrCatalogChangeMakesStoredRecommendationStale() {
        var stored = new StoredRecommendationState("rec-1", BASIS,
            RecommendationDecision.Status.RECOMMENDED, List.of("tool-a"));
        var changed = List.of(
            new PreviewBasis("project-1", 3, "profile-1", 3, "event-1", 4, 5, 6,
                "catalog-1", "a".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 4, "event-1", 4, 5, 6,
                "catalog-1", "a".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 3, "event-2", 4, 5, 6,
                "catalog-1", "a".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 3, "event-1", 5, 5, 6,
                "catalog-1", "a".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 3, "event-1", 4, 6, 6,
                "catalog-1", "a".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 3, "event-1", 4, 5, 7,
                "catalog-1", "a".repeat(64)),
            new PreviewBasis("project-1", 2, "profile-1", 3, "event-1", 4, 5, 6,
                "catalog-2", "b".repeat(64)));
        for (PreviewBasis current : changed) {
            assertEquals(RecommendationReadModel.Validity.STALE,
                RecommendationReadModel.latest(stored, current).validity());
        }
    }

    @Test
    void emptySuccessfulOutcomesRemainReadableButCorruptStateFailsClosed() {
        for (var status : List.of(RecommendationDecision.Status.NO_ADDITIONS_NEEDED,
            RecommendationDecision.Status.NEEDS_INFORMATION,
            RecommendationDecision.Status.NO_COMPATIBLE_TOOLS)) {
            var stored = new StoredRecommendationState("rec-1", BASIS, status, List.of());
            assertEquals(RecommendationReadModel.Validity.CURRENT,
                RecommendationReadModel.latest(stored, BASIS).validity());
        }
        assertThrows(IllegalArgumentException.class,
            () -> RecommendationReadModel.latest(new StoredRecommendationState("rec-1", BASIS,
                RecommendationDecision.Status.RECOMMENDED, List.of()), BASIS));
        assertThrows(IllegalArgumentException.class,
            () -> RecommendationReadModel.latest(new StoredRecommendationState("rec-1", BASIS,
                RecommendationDecision.Status.NO_COMPATIBLE_TOOLS, List.of("tool-a")), BASIS));
        assertThrows(IllegalArgumentException.class,
            () -> RecommendationReadModel.latest(new StoredRecommendationState("rec-1", BASIS,
                RecommendationDecision.Status.RECOMMENDED, List.of("tool-a")), null));
    }

    @Test
    void refusesToProjectRecommendationAgainstAnotherProjectBasis() {
        var stored = new StoredRecommendationState("rec-1", BASIS,
            RecommendationDecision.Status.RECOMMENDED, List.of("tool-a"));
        var otherProject = new PreviewBasis("other-project", 2,
            "profile-1", 3, "event-1", 4, 5, 6, "catalog-1", "a".repeat(64));
        assertThrows(IllegalArgumentException.class,
            () -> RecommendationReadModel.latest(stored, otherProject));
    }
}
