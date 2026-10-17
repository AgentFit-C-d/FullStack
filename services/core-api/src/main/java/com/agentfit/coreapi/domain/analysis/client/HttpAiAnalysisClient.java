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
import java.util.LinkedHashMap;
import java.util.Map;

/** Core-owned HTTP client. It never forwards browser cookies or user credentials. */
public final class HttpAiAnalysisClient implements AiAnalysisClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HmacRequestSigner signer;
    private final URI analysisEndpoint;
    private final URI needsEndpoint;
    private final URI rankEndpoint;

    public HttpAiAnalysisClient(HttpClient httpClient, ObjectMapper objectMapper,
            HmacRequestSigner signer, URI endpoint) {
        this(httpClient, objectMapper, signer, endpoint, endpoint, endpoint);
    }

    public HttpAiAnalysisClient(HttpClient httpClient, ObjectMapper objectMapper,
            HmacRequestSigner signer, URI analysisEndpoint, URI needsEndpoint, URI rankEndpoint) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.analysisEndpoint = analysisEndpoint;
        this.needsEndpoint = needsEndpoint;
        this.rankEndpoint = rankEndpoint;
    }

    @Override
    public AnalysisResponse analyze(AnalysisAttemptContext context, Map<String, Object> input) {
        return post(analysisEndpoint, context, input);
    }

    @Override
    public AnalysisResponse needs(AnalysisAttemptContext context, Map<String, Object> input) {
        return post(needsEndpoint, context, input);
    }

    @Override
    public AnalysisResponse rank(AnalysisAttemptContext context, Map<String, Object> input) {
        return post(rankEndpoint, context, input);
    }

    private AnalysisResponse post(URI endpoint, AnalysisAttemptContext context, Map<String, Object> input) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", context.requestId());
            payload.put("generation", context.generation());
            payload.put("contractVersion", context.contractVersion());
            payload.put("input", input == null ? Map.of() : input);
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
                int status = response.statusCode();
                AiServiceFailureException.Kind kind = status >= 500
                    ? AiServiceFailureException.Kind.PROVIDER
                    : AiServiceFailureException.Kind.INPUT;
                throw new AiServiceFailureException(kind, "AI_HTTP_" + status,
                    "AI service request failed with status " + status);
            }
            AnalysisResponse parsed;
            try {
                parsed = objectMapper.readValue(response.body(), AnalysisResponse.class);
            } catch (Exception ex) {
                throw new AiServiceFailureException(AiServiceFailureException.Kind.CONTRACT,
                    "AI_INVALID_RESPONSE", "AI service response is not valid JSON", ex);
            }
            if (!context.matches(parsed)) {
                throw new AiServiceFailureException(AiServiceFailureException.Kind.CONTRACT,
                    "AI_RESPONSE_CONTEXT_MISMATCH", "AI service response context mismatch");
            }
            return parsed;
        } catch (NotAcceptedException ex) {
            throw ex;
        } catch (AiServiceFailureException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiServiceFailureException(AiServiceFailureException.Kind.TRANSPORT,
                "AI_INTERRUPTED", "AI service request interrupted", ex);
        } catch (Exception ex) {
            throw new AiServiceFailureException(AiServiceFailureException.Kind.TRANSPORT,
                "AI_TRANSPORT_FAILURE", "AI service request failed", ex);
        }
    }
}
