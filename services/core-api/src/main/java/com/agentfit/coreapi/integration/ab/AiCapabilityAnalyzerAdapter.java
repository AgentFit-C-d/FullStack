package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Converts the validated internal AI response into B's existing CapabilityAssessment. */
@Component
public final class AiCapabilityAnalyzerAdapter implements RecommendationCreationService.CapabilityAnalyzer {
    private final AiAnalysisClient client;
    private final ObjectMapper mapper;

    public AiCapabilityAnalyzerAdapter(AiAnalysisClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public RecommendationCreationService.CapabilityAssessment analyze(RecommendationCreationService.AiInput input) {
        String requestId = UUID.randomUUID().toString();
        var context = new AnalysisAttemptContext(requestId, "recommendation", 1L,
            "recommendation-capability-v1", Instant.now().plusSeconds(60), "PROCESSING");
        AiAnalysisClient.AnalysisResponse response = client.analyze(context, Map.of("source", input.aiSourceValues()));
        if (!context.matches(response) || response.result() == null) throw new IllegalArgumentException("invalid AI response");
        Map<?, ?> result = response.result();
        List<AiCapabilityIntake.Claim> claims = mapper.convertValue(result.get("claims"), mapper.getTypeFactory().constructCollectionType(List.class, AiCapabilityIntake.Claim.class));
        List<AiCapabilityIntake.Question> questions = mapper.convertValue(result.get("questions"), mapper.getTypeFactory().constructCollectionType(List.class, AiCapabilityIntake.Question.class));
        return new RecommendationCreationService.CapabilityAssessment(claims, questions);
    }
}
