package com.agentfit.coreapi.domain.analysis;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AnalysisExecutionServiceTest {
    @Test
    void doesNotRetryNotAcceptedFailure() {
        int[] calls = {0};
        AiAnalysisClient client = (context, input) -> {
            calls[0]++;
            throw new AiAnalysisClient.NotAcceptedException();
        };
        AnalysisExecutionService service = new AnalysisExecutionService(client);

        assertThrows(AiAnalysisClient.NotAcceptedException.class, () -> service.execute(
            new AnalysisAttemptContext("r-1", "p-1", 1L, "v1", java.time.Instant.now().plusSeconds(60), "PROCESSING"),
            Map.of("fixture", "test")));

        assertEquals(1, calls[0]);
    }

    @Test
    void mapsCoreOwnedDocumentToTransientBase64Input() {
        Map<String, Object>[] captured = new Map[] { null };
        AiAnalysisClient client = (context, input) -> {
            captured[0] = input;
            return new AiAnalysisClient.AnalysisResponse(context.requestId(), context.generation(),
                context.contractVersion(), Map.of("kind", "STRUCTURED"));
        };
        AnalysisExecutionService service = new AnalysisExecutionService(client);
        var context = new AnalysisAttemptContext("r-doc", "p-1", 1L, "v1",
            Instant.now().plusSeconds(60), "PROCESSING");
        var document = new com.agentfit.coreapi.domain.project.entity.PlanningInput(
            "p-1", 1L, "MARKDOWN", "plan.md", "text/markdown", 4L, 4, null,
            "plan".getBytes(StandardCharsets.UTF_8), "plan", null, Instant.now());

        service.execute(context, document);

        assertEquals("markdown", captured[0].get("kind"));
        assertEquals("r-doc", captured[0].get("documentId"));
        assertEquals("cGxhbg==", captured[0].get("contentBase64"));
    }

    @Test
    void doesNotRetryOrConvertProviderFailureIntoAValidResult() {
        int[] calls = {0};
        AiAnalysisClient client = (context, input) -> {
            calls[0]++;
            throw new AiServiceFailureException(AiServiceFailureException.Kind.PROVIDER,
                "AI_HTTP_500", "provider unavailable");
        };
        var failure = assertThrows(AiServiceFailureException.class, () -> new AnalysisExecutionService(client).execute(
            new AnalysisAttemptContext("r-failure", "p-1", 1L, "v1", Instant.now().plusSeconds(60), "PROCESSING"),
            Map.of()));
        assertEquals(AiServiceFailureException.Kind.PROVIDER, failure.kind());
        assertEquals(1, calls[0]);
    }
}



