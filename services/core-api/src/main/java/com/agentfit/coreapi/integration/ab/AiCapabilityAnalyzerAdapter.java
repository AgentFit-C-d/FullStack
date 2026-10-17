package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.client.AiServiceFailureException;
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
        try {
            AiAnalysisClient.AnalysisResponse response = client.needs(context, Map.of("facts", facts(input.aiSourceValues())));
            if (!context.matches(response) || response.result() == null) {
                throw new AiServiceFailureException(AiServiceFailureException.Kind.CONTRACT,
                    "AI_RESPONSE_CONTEXT_MISMATCH", "invalid AI response context");
            }
            Map<?, ?> result = response.result();
            List<AiCapabilityIntake.Claim> claims = mapper.convertValue(result.get("claims"), mapper.getTypeFactory().constructCollectionType(List.class, AiCapabilityIntake.Claim.class));
            List<AiCapabilityIntake.Question> questions = mapper.convertValue(result.get("questions"), mapper.getTypeFactory().constructCollectionType(List.class, AiCapabilityIntake.Question.class));
            var validated = AiCapabilityIntake.validate(claims, questions, input.aiSourceValues().keySet());
            return new RecommendationCreationService.CapabilityAssessment(validated.claims(), validated.questions());
        } catch (AiServiceFailureException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiServiceFailureException(AiServiceFailureException.Kind.CONTRACT,
                "AI_CAPABILITY_CONTRACT_INVALID", "AI Capability response is invalid", ex);
        }
    }

    private static List<Map<String, Object>> facts(Map<String, String> sourceValues) {
        return sourceValues.entrySet().stream().map(entry -> Map.<String, Object>of(
            "id", entry.getKey(),
            "field", fieldFor(entry.getKey()),
            "value", entry.getValue()))
            .toList();
    }

    private static String fieldFor(String sourcePath) {
        if (sourcePath.startsWith("profile.item.")) {
            String[] parts = sourcePath.split("\\.");
            return parts.length >= 3 ? parts[2] : "unknown";
        }
        return switch (sourcePath) {
            case "profile.projectName" -> "project_name";
            case "developer.version" -> "backend";
            case "environment.osFamily", "environment.clientId", "environment.clientVersion" -> "deployment";
            default -> "unknown";
        };
    }
}
