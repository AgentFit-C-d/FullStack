package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisInputLease;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisInputLeaseRepository;
import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import com.agentfit.coreapi.domain.project.repository.PlanningInputRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
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
    private final PlanningInputRepository inputs;
    private final InternalInputAccessService inputAccess;
    private final AnalysisExecutionService execution;
    private final AnalysisWorkerTerminalCoordinator terminal;
    private final Clock clock;
    private final long leaseSeconds;

    public AnalysisExecutionScheduler(
            AnalysisAttemptRepository attempts,
            AnalysisInputLeaseRepository leases,
            PlanningInputRepository inputs,
            InternalInputAccessService inputAccess,
            AnalysisExecutionService execution,
            AnalysisWorkerTerminalCoordinator terminal,
            Clock clock,
            @Value("${agentfit.analysis.worker-lease-seconds:180}") long leaseSeconds) {
        this.attempts = attempts;
        this.leases = leases;
        this.inputs = inputs;
        this.inputAccess = inputAccess;
        this.execution = execution;
        this.terminal = terminal;
        this.clock = clock;
        if (leaseSeconds < 1) throw new IllegalArgumentException("worker lease must be positive");
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${agentfit.analysis.execution-interval-seconds:1}000")
    public void executePending() {
        Instant now = Instant.now(clock);
        attempts.claimNext(now, now.plusSeconds(leaseSeconds)).ifPresent(this::executeOne);
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
            AiAnalysisClient.AnalysisResponse response = execution.execute(
                    context,
                    buildAiInput(attempt, input, lease));
            terminal.succeed(attempt, response);
        } catch (AiAnalysisClient.NotAcceptedException exception) {
            terminal.fail(attempt, "AI_SERVICE_NOT_ACCEPTED");
        } catch (RuntimeException exception) {
            terminal.fail(attempt, safeErrorCode(exception));
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
