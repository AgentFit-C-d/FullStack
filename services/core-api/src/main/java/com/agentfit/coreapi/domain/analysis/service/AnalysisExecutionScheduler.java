package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisInputLease;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisResult;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisInputLeaseRepository;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisResultRepository;
import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.project.repository.PlanningInputRepository;
import com.agentfit.coreapi.domain.project.repository.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agentfit.analysis.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisExecutionScheduler {
    private static final String STORAGE = "local";
    private static final String PROVIDER = "upstage";

    private final AnalysisAttemptRepository attempts;
    private final AnalysisInputLeaseRepository leases;
    private final AnalysisResultRepository results;
    private final PlanningInputRepository inputs;
    private final ProjectRepository projects;
    private final InternalInputAccessService inputAccess;
    private final AnalysisExecutionService execution;
    private final ProjectAnalysisService analysisService;
    private final Clock clock;

    public AnalysisExecutionScheduler(
            AnalysisAttemptRepository attempts,
            AnalysisInputLeaseRepository leases,
            AnalysisResultRepository results,
            PlanningInputRepository inputs,
            ProjectRepository projects,
            InternalInputAccessService inputAccess,
            AnalysisExecutionService execution,
            ProjectAnalysisService analysisService,
            Clock clock) {
        this.attempts = attempts;
        this.leases = leases;
        this.results = results;
        this.inputs = inputs;
        this.projects = projects;
        this.inputAccess = inputAccess;
        this.execution = execution;
        this.analysisService = analysisService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${agentfit.analysis.execution-interval-seconds:1}000")
    public void executePending() {
        for (AnalysisAttempt attempt : attempts.findProcessingAttempts()) {
            executeOne(attempt);
        }
    }

    void executeOne(AnalysisAttempt attempt) {
        try {
            PlanningInput input = inputs.findByProjectIdAndRevision(
                            attempt.projectId(),
                            requireInputRevision(attempt))
                    .orElseThrow(() -> new IllegalArgumentException("Planning input is not found"));
            AnalysisInputLease lease = leases.findByAttemptId(attempt.id())
                    .orElseGet(() -> inputAccess.issueLease(
                            attempt,
                            input.revision(),
                            attempt.deadlineAt()));
            AnalysisAttemptContext context = new AnalysisAttemptContext(
                    attempt.requestId(),
                    attempt.projectId(),
                    attempt.generation(),
                    attempt.contractVersion(),
                    attempt.deadlineAt(),
                    attempt.state());
            Project project = projects.findByIdAndOwnerUuid(
                            attempt.projectId(),
                            attempt.ownerUuid())
                    .orElseThrow(() -> new IllegalArgumentException("Project is not found"));
            AiAnalysisClient.AnalysisResponse response = execution.execute(
                    context,
                    buildAiInput(attempt, input, lease));
            if (!new com.agentfit.coreapi.domain.project.service.ResultPersistenceGuard(value -> value)
                    .canPersist(context, project, Instant.now(clock))) {
                throw new IllegalStateException("Analysis result is no longer valid");
            }
            results.save(new AnalysisResult(
                    attempt.id(),
                    response.requestId(),
                    response.generation(),
                    response.contractVersion(),
                    response.result(),
                    Instant.now(clock)));
            analysisService.complete(attempt, "SUCCEEDED", null);
        } catch (AiAnalysisClient.NotAcceptedException exception) {
            analysisService.complete(attempt, "FAILED", "AI_SERVICE_NOT_ACCEPTED");
        } catch (RuntimeException exception) {
            analysisService.complete(attempt, "FAILED", safeErrorCode(exception));
        }
    }

    private Map<String, Object> buildAiInput(
            AnalysisAttempt attempt,
            PlanningInput input,
            AnalysisInputLease lease) {
        // The persisted attempt ID is UUID throughout Core and internal input APIs.
        String documentId = attempt.id();
        return Map.of(
                "documentId", documentId,
                "attemptId", documentId,
                "leaseId", lease.leaseId(),
                "inputKind", inputKind(input.inputType()),
                "inputRef", Map.of(
                        "uploadId", lease.uploadId(),
                        "storage", STORAGE,
                        "objectKey", "inputs/" + lease.uploadId(),
                        "expiresAt", lease.expiresAt().toString()),
                "provider", PROVIDER,
                "deadline", attempt.deadlineAt().toString());
    }

    private long requireInputRevision(AnalysisAttempt attempt) {
        if (attempt.inputRevision() == null) {
            throw new IllegalArgumentException("Analysis input revision is missing");
        }
        return attempt.inputRevision();
    }

    private String inputKind(String inputType) {
        return switch (inputType) {
            case "PDF" -> "pdf";
            case "MARKDOWN" -> "markdown";
            case "TEXT" -> "text";
            default -> throw new IllegalArgumentException("Unsupported planning input type");
        };
    }

    private String safeErrorCode(RuntimeException exception) {
        String code = exception.getMessage();
        return code == null || code.isBlank() ? "ANALYSIS_FAILED" : code.substring(0, Math.min(64, code.length()));
    }
}
