package com.agentfit.coreapi.recommendation;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationLatestServiceTest {
    private static final PreviewBasis BASIS = new PreviewBasis("project-1", 2,
        "profile-1", 3, "event-1", 4, 5, 6, "catalog-1", "a".repeat(64));

    @Test
    void readsOwnerCheckedBasisFirstAndProjectsStoredResultWithoutAiCall() {
        AtomicInteger storedReads = new AtomicInteger();
        var service = new RecommendationLatestService(
            projectId -> BASIS,
            projectId -> {
                storedReads.incrementAndGet();
                return new StoredRecommendationState("rec-1", BASIS,
                    RecommendationDecision.Status.RECOMMENDED, List.of("tool-a"));
            });

        var latest = service.latest("project-1");

        assertEquals(RecommendationReadModel.Validity.CURRENT, latest.validity());
        assertEquals("rec-1", latest.recommendationId());
        assertEquals(1, storedReads.get());
    }

    @Test
    void returnsEmptyWhenOwnerHasNoStoredRecommendation() {
        var service = new RecommendationLatestService(projectId -> BASIS, projectId -> null);
        assertNull(service.latest("project-1"));
    }

    @Test
    void refusesForeignCurrentBasisBeforeStoredLookup() {
        AtomicInteger storedReads = new AtomicInteger();
        var service = new RecommendationLatestService(
            projectId -> new PreviewBasis("other-project", 2,
                "profile-1", 3, "event-1", 4, 5, 6, "catalog-1", "a".repeat(64)),
            projectId -> { storedReads.incrementAndGet(); return null; });

        assertThrows(IllegalArgumentException.class, () -> service.latest("project-1"));
        assertEquals(0, storedReads.get());
    }

    @Test
    void returnsStaleWhenOwnerCurrentBasisChanges() {
        var changed = new PreviewBasis("project-1", 3,
            "profile-1", 3, "event-1", 4, 5, 6, "catalog-1", "a".repeat(64));
        var service = new RecommendationLatestService(projectId -> changed,
            projectId -> new StoredRecommendationState("rec-1", BASIS,
                RecommendationDecision.Status.RECOMMENDED, List.of("tool-a")));

        assertEquals(RecommendationReadModel.Validity.STALE,
            service.latest("project-1").validity());
    }
}
