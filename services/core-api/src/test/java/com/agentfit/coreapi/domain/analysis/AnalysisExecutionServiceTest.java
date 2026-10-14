package com.agentfit.coreapi.domain.analysis;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AnalysisExecutionServiceTest {
    @Test
    void retriesOnlyAConnectionNotAcceptedFailureOnce() {
        int[] calls = {0};
        AiAnalysisClient client = (context, input) -> {
            calls[0]++;
            if (calls[0] == 1) {
                throw new AiAnalysisClient.NotAcceptedException();
            }
            return new AiAnalysisClient.AnalysisResponse("r-1", 1L, "v1", Map.of("kind", "CONTRACT_TEST_DOUBLE"));
        };
        AnalysisExecutionService service = new AnalysisExecutionService(client);

        AiAnalysisClient.AnalysisResponse response = service.execute(
            new AnalysisAttemptContext("r-1", "p-1", 1L, "v1", java.time.Instant.now().plusSeconds(60), "PROCESSING"),
            Map.of("fixture", "test"));

        assertEquals(2, calls[0]);
        assertEquals("CONTRACT_TEST_DOUBLE", response.result().get("kind"));
    }
}



