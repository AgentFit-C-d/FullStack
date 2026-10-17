package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.project.repository.PlanningInputRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls one Core-owned analysis attempt. Claiming happens before, and terminal persistence after, the AI call. */
@Component
public class AnalysisWorker {
    private final AnalysisAttemptRepository attempts;
    private final PlanningInputRepository inputs;
    private final AnalysisExecutionService execution;
    private final AnalysisWorkerTerminalCoordinator terminal;
    private final Clock clock;
    private final long leaseSeconds;

    @Autowired
    public AnalysisWorker(AnalysisAttemptRepository attempts, PlanningInputRepository inputs,
            AnalysisExecutionService execution, AnalysisWorkerTerminalCoordinator terminal, Clock clock,
            @Value("${agentfit.analysis.worker-lease-seconds:180}") long leaseSeconds) {
        if (leaseSeconds < 1) throw new IllegalArgumentException("worker lease must be positive");
        this.attempts = attempts; this.inputs = inputs; this.execution = execution; this.terminal = terminal;
        this.clock = clock; this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${agentfit.analysis.worker-poll-interval-ms:1000}")
    public void poll() { processOne(); }

    public void processOne() {
        Instant now = Instant.now(clock);
        attempts.claimNext(now, now.plusSeconds(leaseSeconds)).ifPresent(this::executeClaimed);
    }

    private void executeClaimed(AnalysisAttempt attempt) {
        try {
            if (attempt.inputRevision() == null) throw new IllegalStateException("analysis input revision is missing");
            var input = inputs.findByProjectIdAndRevision(attempt.projectId(), attempt.inputRevision())
                .orElseThrow(() -> new IllegalStateException("analysis input is unavailable"));
            var context = new AnalysisAttemptContext(attempt.requestId(), attempt.projectId(), attempt.generation(),
                attempt.contractVersion(), attempt.deadlineAt(), attempt.status());
            terminal.succeed(attempt, execution.execute(context, input));
        } catch (RuntimeException ex) {
            terminal.fail(attempt, Instant.now(clock).isAfter(attempt.deadlineAt()) ? "TIMEOUT" : "AI_ANALYSIS_FAILED");
        }
    }
}
