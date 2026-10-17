package com.agentfit.coreapi.domain.analysis.service;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.time.Instant;
import java.util.function.Consumer;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
public class AnalysisExecutionService {
    private final AiAnalysisClient client;
    private final ResultPersistenceGuard persistenceGuard;
    private final AnalysisAttemptRepository attemptRepository;

    public AnalysisExecutionService(AiAnalysisClient client) {
        this(client, new ResultPersistenceGuard(value -> value), new AnalysisAttemptRepository() { });
    }

    public AnalysisExecutionService(AiAnalysisClient client, ResultPersistenceGuard persistenceGuard) {
        this(client, persistenceGuard, new AnalysisAttemptRepository() { });
    }

    public AnalysisExecutionService(AiAnalysisClient client, ResultPersistenceGuard persistenceGuard,
            AnalysisAttemptRepository attemptRepository) {
        this.client = client;
        this.persistenceGuard = persistenceGuard;
        this.attemptRepository = attemptRepository;
    }

    public AiAnalysisClient.AnalysisResponse execute(AnalysisAttemptContext context, Map<String, Object> input) {
        if (context == null || !context.isAcceptable(Instant.now())) {
            throw new IllegalStateException("analysis attempt is not accepting results");
        }
        return validate(context, client.analyze(context, input));
    }

    /** Builds the transient AI input from Core-owned bytes without giving AI a storage reference. */
    public AiAnalysisClient.AnalysisResponse execute(AnalysisAttemptContext context,
            com.agentfit.coreapi.domain.project.entity.PlanningInput document) {
        if (document == null || !document.hasRawContent()) {
            throw new IllegalArgumentException("analysis input is unavailable");
        }
        byte[] bytes = document.rawContent() != null
            ? document.rawContent()
            : document.extractedText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("kind", document.inputType().toLowerCase(java.util.Locale.ROOT));
        input.put("documentId", context.requestId());
        input.put("contentBase64", Base64.getEncoder().encodeToString(bytes));
        input.put("deadline", context.deadlineAt().toString());
        return execute(context, input);
    }

    public AiAnalysisClient.AnalysisResponse executeAndPersist(AnalysisAttemptContext context,
            Map<String, Object> input, com.agentfit.coreapi.domain.project.entity.Project project,
            Consumer<AiAnalysisClient.AnalysisResponse> persist) {
        AiAnalysisClient.AnalysisResponse response = execute(context, input);
        if (!persistenceGuard.canPersist(context, project, Instant.now())) {
            throw new IllegalStateException("analysis result is no longer valid");
        }
        persist.accept(response);
        attemptRepository.complete(context.requestId(), "SUCCEEDED", Instant.now());
        return response;
    }

    /** Standard A path: validate the response, persist only the structured DRAFT, then complete the attempt. */
    public AnalysisResultPersistenceService.DraftSaved executeAndPersist(
            AnalysisAttemptContext context, Map<String, Object> input,
            com.agentfit.coreapi.domain.project.entity.Project project,
            AnalysisResultPersistenceService persistence) {
        AiAnalysisClient.AnalysisResponse response = execute(context, input);
        if (!persistenceGuard.canPersist(context, project, Instant.now())) {
            throw new IllegalStateException("analysis result is no longer valid");
        }
        AnalysisResultPersistenceService.DraftSaved saved = persistence.persist(context, project, response);
        attemptRepository.complete(context.requestId(), "SUCCEEDED", Instant.now());
        return saved;
    }

    public AnalysisAttempt startAttempt(String projectId, String requestId, long generation,
            String contractVersion, Instant deadlineAt) {
        AnalysisAttempt attempt = new AnalysisAttempt(0L, projectId, requestId, generation,
            contractVersion, "PROCESSING", deadlineAt, Instant.now(), null);
        return attemptRepository.start(attempt);
    }

    private AiAnalysisClient.AnalysisResponse validate(AnalysisAttemptContext context,
            AiAnalysisClient.AnalysisResponse response) {
        if (!context.matches(response)) {
            throw new IllegalStateException("analysis response context mismatch");
        }
        return response;
    }
}



