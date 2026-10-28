package com.agentfit.coreapi.integration.ab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.client.AiServiceFailureException;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiCapabilityAnalyzerAdapterContractTest {
    @Test
    void searchNeedsOnlyResponseCannotBeSavedAsCapabilityAssessment() {
        AiAnalysisClient client = new AiAnalysisClient() {
            @Override
            public AnalysisResponse analyze(AnalysisAttemptContext context, Map<String, Object> input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AnalysisResponse needs(AnalysisAttemptContext context, Map<String, Object> input) {
                return new AnalysisResponse(context.requestId(), context.generation(),
                    context.contractVersion(), Map.of("needs", List.of(Map.of("id", "n1"))));
            }
        };
        var adapter = new AiCapabilityAnalyzerAdapter(client, new ObjectMapper());

        var error = assertThrows(AiServiceFailureException.class,
            () -> adapter.analyze(new RecommendationCreationService.AiInput(
                Map.of("profile.item.features.0", "Write tests"))));
        assertEquals(AiServiceFailureException.Kind.CONTRACT, error.kind());
        assertEquals("AI_NEEDS_ONLY_RESPONSE", error.code());
    }
}
