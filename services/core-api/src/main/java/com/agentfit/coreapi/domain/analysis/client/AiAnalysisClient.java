package com.agentfit.coreapi.domain.analysis.client;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import java.util.Map;

public interface AiAnalysisClient {
    AnalysisResponse analyze(AnalysisAttemptContext context, Map<String, Object> input);

    class NotAcceptedException extends RuntimeException {
    }

    record AnalysisResponse(String requestId, long generation, String contractVersion, Map<String, Object> result) {
    }
}



