package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAdmissionRepository;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.audit.entity.AuditEvent;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import com.agentfit.coreapi.domain.project.repository.PlanningInputRepository;
import com.agentfit.coreapi.domain.project.repository.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes terminal Worker state in Core's database after the external AI call has returned. */
@Service
public class DefaultAnalysisWorkerTerminalCoordinator implements AnalysisWorkerTerminalCoordinator {
    private final ProjectRepository projects;
    private final AnalysisAttemptRepository attempts;
    private final AnalysisAdmissionRepository admission;
    private final PlanningInputRepository inputs;
    private final AnalysisResultPersistenceService persistence;
    private final AuditRecorder audit;
    private final Clock clock;

    public DefaultAnalysisWorkerTerminalCoordinator(ProjectRepository projects, AnalysisAttemptRepository attempts,
            AnalysisAdmissionRepository admission, PlanningInputRepository inputs,
            AnalysisResultPersistenceService persistence, AuditRecorder audit, Clock clock) {
        this.projects = projects; this.attempts = attempts; this.admission = admission; this.inputs = inputs;
        this.persistence = persistence; this.audit = audit; this.clock = clock;
    }

    @Override
    @Transactional
    public void succeed(AnalysisAttempt attempt, AiAnalysisClient.AnalysisResponse response) {
        var project = projects.findByIdForUpdate(attempt.projectId())
            .orElseThrow(() -> new IllegalStateException("analysis project no longer exists"));
        if (project.analysisGeneration() != attempt.generation()
                || (attempt.expectedProjectVersion() != null && project.version() != attempt.expectedProjectVersion())) {
            failInsideTransaction(attempt, "STALE_ANALYSIS_RESULT");
            return;
        }
        AnalysisAttemptContext context = new AnalysisAttemptContext(attempt.requestId(), attempt.projectId(),
            attempt.generation(), attempt.contractVersion(), attempt.deadlineAt(), attempt.status());
        AnalysisResultPersistenceService.DraftSaved saved = persistence.persist(context, project, response);
        Instant now = Instant.now(clock);
        if (!attempts.transitionToSucceeded(attempt.id(), requireToken(attempt), saved.draftId(), saved.draftVersion(), now)) {
            throw new IllegalStateException("analysis claim was lost before success persistence");
        }
        scrubAndRelease(attempt);
        audit(attempt, "ANALYSIS_COMPLETED", "SUCCEEDED", null);
    }

    @Override
    @Transactional
    public void fail(AnalysisAttempt attempt, String errorCode) {
        failInsideTransaction(attempt, errorCode == null ? "AI_ANALYSIS_FAILED" : errorCode);
    }

    private void failInsideTransaction(AnalysisAttempt attempt, String errorCode) {
        if (attempts.transitionToTerminal(attempt.id(), requireToken(attempt), "FAILED", errorCode, Instant.now(clock))) {
            scrubAndRelease(attempt);
            audit(attempt, "ANALYSIS_COMPLETED", "FAILED", errorCode);
        }
    }

    private void scrubAndRelease(AnalysisAttempt attempt) {
        if (attempt.inputRevision() != null) inputs.scrub(attempt.projectId(), attempt.inputRevision());
        admission.lock();
        admission.decrement();
    }

    private void audit(AnalysisAttempt attempt, String action, String outcome, String reason) {
        if (attempt.ownerUuid() != null) {
            audit.record(new AuditEvent(attempt.ownerUuid(), UUID.fromString(attempt.projectId()), action,
                outcome, Instant.now(clock), reason, Map.of("status", outcome)));
        }
    }

    private UUID requireToken(AnalysisAttempt attempt) {
        if (attempt.executionToken() == null) throw new IllegalStateException("analysis attempt has no worker claim");
        return attempt.executionToken();
    }
}
