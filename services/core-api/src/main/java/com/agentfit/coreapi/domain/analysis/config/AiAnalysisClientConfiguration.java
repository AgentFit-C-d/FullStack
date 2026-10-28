package com.agentfit.coreapi.domain.analysis.config;

import com.agentfit.coreapi.domain.analysis.client.HttpAiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.service.HmacRequestSigner;
import com.agentfit.coreapi.domain.analysis.service.HmacKeyRing;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
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
            @Value("${agentfit.ai-service.url:http://localhost:8001/internal/v1/analysis}") String url) {
        // Uvicorn's local HTTP/1.1 endpoint must receive the exact JSON bytes used for HMAC.
        // Pinning the protocol avoids an h2c/HTTP-1.1 negotiation that can drop the body.
        HttpClient client = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
        return new HttpAiAnalysisClient(client, objectMapper, signer, URI.create(url));
    }
}
