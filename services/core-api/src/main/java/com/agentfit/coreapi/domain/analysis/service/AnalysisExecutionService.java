package com.agentfit.coreapi.domain.analysis.service;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import java.util.Map;
import java.time.Instant;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
@Service
public class AnalysisExecutionService {
    private final AiAnalysisClient client;
    private final ResultPersistenceGuard persistenceGuard;
    private final AnalysisAttemptRepository attemptRepository;

    @Autowired
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
        try {
            return validate(context, client.analyze(context, input));
        } catch (AiAnalysisClient.NotAcceptedException firstFailure) {
            return validate(context, client.analyze(context, input));
        }
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



