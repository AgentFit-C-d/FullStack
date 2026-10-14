package com.agentfit.coreapi.domain.analysis.config;

import com.agentfit.coreapi.domain.analysis.client.HttpAiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.service.HmacRequestSigner;
import com.agentfit.coreapi.domain.analysis.service.HmacKeyRing;
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
            @Value("${agentfit.ai-service.url:http://localhost:8000/internal/v1/analysis}") String url) {
        return new HttpAiAnalysisClient(HttpClient.newHttpClient(), objectMapper, signer, URI.create(url));
    }
}
