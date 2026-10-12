package com.agentfit.coreapi.domain.analysis.client;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.analysis.service.HmacRequestSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/** Core-owned HTTP client. It never forwards browser cookies or user credentials. */
public final class HttpAiAnalysisClient implements AiAnalysisClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HmacRequestSigner signer;
    private final URI endpoint;

    public HttpAiAnalysisClient(HttpClient httpClient, ObjectMapper objectMapper,
            HmacRequestSigner signer, URI endpoint) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.endpoint = endpoint;
    }

    @Override
    public AnalysisResponse analyze(AnalysisAttemptContext context, Map<String, Object> input) {
        try {
            Map<String, Object> payload = Map.of("requestId", context.requestId(),
                "generation", context.generation(), "contractVersion", context.contractVersion(),
                "input", input == null ? Map.of() : input);
            byte[] body = objectMapper.writeValueAsBytes(payload);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            HmacRequestSigner.SignedRequest signature = signer.sign("POST", endpoint.getPath(), timestamp,
                context.requestId(), context.generation(), context.contractVersion(), body);
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .header("X-AgentFit-Key-Id", signature.keyId())
                .header("X-AgentFit-Timestamp", signature.timestamp())
                .header("X-AgentFit-Signature", signature.signature())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 503) {
                throw new NotAcceptedException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI service request failed with status " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), AnalysisResponse.class);
        } catch (NotAcceptedException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI service request interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("AI service request failed", ex);
        }
    }
}
