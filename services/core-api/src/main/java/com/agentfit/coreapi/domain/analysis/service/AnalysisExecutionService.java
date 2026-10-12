package com.agentfit.coreapi.domain.analysis.service;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import java.util.Map;

public class AnalysisExecutionService {
    private final AiAnalysisClient client;

    public AnalysisExecutionService(AiAnalysisClient client) {
        this.client = client;
    }

    public AiAnalysisClient.AnalysisResponse execute(AnalysisAttemptContext context, Map<String, Object> input) {
        try {
            return client.analyze(context, input);
        } catch (AiAnalysisClient.NotAcceptedException firstFailure) {
            return client.analyze(context, input);
        }
    }
}



