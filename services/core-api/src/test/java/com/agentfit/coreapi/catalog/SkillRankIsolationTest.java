package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.analysis.service.SkillRankPreviewService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillRankIsolationTest {
    @Test
    void rankCallsOnlyTheInternalScoringClientAndDoesNotPersistRecommendationState() {
        AiAnalysisClient client = mock(AiAnalysisClient.class);
        when(client.rank(any(AnalysisAttemptContext.class), any(Map.class)))
            .thenAnswer(invocation -> {
                AnalysisAttemptContext context = invocation.getArgument(0);
                return new AiAnalysisClient.AnalysisResponse(context.requestId(), context.generation(),
                    context.contractVersion(), Map.of("matches", List.of(Map.of("id", "c1", "score", 80))));
            });
        var response = new SkillRankPreviewService(client).score(
            List.of(Map.of("id", "n1")), List.of(Map.of("id", "c1", "name", "one", "content", "text")));
        var matches = (List<?>) response.result().get("matches");
        assertEquals(80, ((Map<?, ?>) matches.getFirst()).get("score"));
        verify(client).rank(any(AnalysisAttemptContext.class), any(Map.class));
        verifyNoMoreInteractions(client);
    }

    @Test
    void rankCannotAcceptAnUnboundedCandidateList() {
        assertThrows(IllegalArgumentException.class,
            () -> new SkillRankPreviewService(mock(AiAnalysisClient.class)).score(List.of(), List.of(
                Map.of("id", "1"), Map.of("id", "2"), Map.of("id", "3"), Map.of("id", "4"),
                Map.of("id", "5"), Map.of("id", "6"), Map.of("id", "7"), Map.of("id", "8"),
                Map.of("id", "9"), Map.of("id", "10"), Map.of("id", "11"), Map.of("id", "12"),
                Map.of("id", "13"), Map.of("id", "14"), Map.of("id", "15"), Map.of("id", "16"))));
    }
}
