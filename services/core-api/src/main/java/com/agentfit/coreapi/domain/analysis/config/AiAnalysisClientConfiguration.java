package com.agentfit.coreapi.domain.analysis.config;

import com.agentfit.coreapi.domain.analysis.client.HttpAiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.service.HmacRequestSigner;
import com.agentfit.coreapi.domain.analysis.service.HmacKeyRing;
import com.agentfit.coreapi.domain.analysis.service.AnalysisExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiAnalysisClientConfiguration {
    @Bean
    HmacRequestSigner hmacRequestSigner(HmacKeyRing keyRing) {
        return new HmacRequestSigner(keyRing);
    }

    @Bean
    HttpAiAnalysisClient aiAnalysisClient(ObjectMapper objectMapper, HmacRequestSigner signer,
            @Value("${agentfit.ai-service.analysis-url:http://localhost:8000/internal/v1/analysis}") String analysisUrl,
            @Value("${agentfit.ai-service.needs-url:http://localhost:8000/internal/v1/skills/needs}") String needsUrl,
            @Value("${agentfit.ai-service.rank-url:http://localhost:8000/internal/v1/skills/rank}") String rankUrl) {
        // Uvicorn does not accept the default Java client's h2c upgrade with a signed POST body.
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return new HttpAiAnalysisClient(httpClient, objectMapper, signer,
            URI.create(analysisUrl), URI.create(needsUrl), URI.create(rankUrl));
    }

    @Bean
    AnalysisExecutionService analysisExecutionService(HttpAiAnalysisClient client) {
        return new AnalysisExecutionService(client);
    }
}
