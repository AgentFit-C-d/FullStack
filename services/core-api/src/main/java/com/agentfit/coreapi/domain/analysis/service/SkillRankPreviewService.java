package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rank is an internal candidate-scoring preview. It has no dependency on B's recommendation
 * store and therefore cannot change recommendation status, items, or basis.
 */
public final class SkillRankPreviewService {
    private final AiAnalysisClient client;

    public SkillRankPreviewService(AiAnalysisClient client) { this.client = Objects.requireNonNull(client); }

    public AiAnalysisClient.AnalysisResponse score(List<?> needs, List<?> candidates) {
        if (needs == null || candidates == null || needs.size() > 6 || candidates.size() > 15) {
            throw new IllegalArgumentException("rank input exceeds bounded limits");
        }
        String requestId = "rank-" + java.util.UUID.randomUUID();
        AnalysisAttemptContext context = new AnalysisAttemptContext(requestId, "rank-preview", 1L,
            "rank-v1", Instant.now().plusSeconds(60), "PROCESSING");
        var response = client.rank(context, Map.of("needs", needs, "candidates", candidates));
        if (!context.matches(response) || response.result() == null) {
            throw new IllegalArgumentException("invalid rank response");
        }
        return response;
    }
}
