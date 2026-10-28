package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAdmissionRepository;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisResultRepository;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisResult;
import com.agentfit.coreapi.domain.analysis.exception.AnalysisResultNotFoundException;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.project.exception.AnalysisAlreadyProcessingException;
import com.agentfit.coreapi.domain.project.exception.AnalysisCapacityExceededException;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.global.exception.VersionConflictException;
import com.agentfit.coreapi.domain.project.repository.PlanningInputRepository;
import com.agentfit.coreapi.domain.project.repository.ProjectRepository;
import com.agentfit.coreapi.global.exception.ErrorCode;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import com.agentfit.coreapi.domain.audit.entity.AuditEvent;

@Service
public class ProjectAnalysisService {
    private static final long NEW_ATTEMPT_ID = 0L;
    private static final String ANALYSIS_VERSION = "1.0";
    private static final String PROCESSING_STATE = "PROCESSING";

    private final ProjectRepository projects;
    private final AnalysisAttemptRepository attempts;
    private final AnalysisResultRepository results;
    private final AnalysisAdmissionRepository admission;
    private final PlanningInputRepository inputs;
    private final Clock clock;
    private final long timeoutSeconds;
    private final AuditRecorder audit;

    public ProjectAnalysisService(
            ProjectRepository projects,
            AnalysisAttemptRepository attempts,
            AnalysisAdmissionRepository admission,
            PlanningInputRepository inputs,
            Clock clock,
            @Value("${agentfit.analysis.timeout-seconds:600}") long timeoutSeconds) {
        this(projects, attempts, admission, inputs, clock, timeoutSeconds, new AuditRecorder(), new AnalysisResultRepository() { });
    }

    @Autowired
    public ProjectAnalysisService(
            ProjectRepository projects,
            AnalysisAttemptRepository attempts,
            AnalysisAdmissionRepository admission,
            PlanningInputRepository inputs,
            Clock clock,
            @Value("${agentfit.analysis.timeout-seconds:600}") long timeoutSeconds,
            AuditRecorder audit,
            AnalysisResultRepository results) {
        this.projects = projects;
        this.attempts = attempts;
        this.results = results;
        this.admission = admission;
        this.inputs = inputs;
        this.clock = clock;
        this.timeoutSeconds = timeoutSeconds;
        this.audit = audit;
    }

    public ProjectAnalysisService(
            ProjectRepository projects,
            AnalysisAttemptRepository attempts,
            AnalysisAdmissionRepository admission,
            PlanningInputRepository inputs,
            Clock clock,
            long timeoutSeconds,
            AuditRecorder audit) {
        this(projects, attempts, admission, inputs, clock, timeoutSeconds, audit, new AnalysisResultRepository() { });
    }
    @Transactional
    public AnalysisAttempt start(
            AuthenticatedPrincipal principal,
            String projectId,
            long expectedVersion) {
        requireAuthenticated(principal);

        Project ownedProject = findOwnedProject(projectId, principal.userId());
        Project lockedProject = findLockedProject(projectId, ownedProject);
        validateExpectedVersion(lockedProject, expectedVersion);
        requirePlanningInput(projectId);
        requireProjectIsNotProcessing(projectId);
        requireUserCapacity(principal.userId());
        reserveServerCapacity();

        Instant startedAt = Instant.now(clock);
        long generation = lockedProject.analysisGeneration() + 1;
        AnalysisAttempt attempt = createProcessingAttempt(
                principal,
                lockedProject,
                projectId,
                generation,
                startedAt);

        AnalysisAttempt savedAttempt = attempts.start(attempt);
        projects.markAnalysisStarted(
                projectId,
                generation,
                lockedProject.version() + 1,
                startedAt);
        recordAnalysisStarted(principal, projectId, startedAt);

        return savedAttempt;
    }

    private void requireAuthenticated(AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
    }

    private Project findOwnedProject(String projectId, long ownerId) {
        return projects.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(ProjectNotFoundException::new);
    }

    private Project findLockedProject(String projectId, Project ownedProject) {
        return projects.findByIdForUpdate(projectId)
                .orElse(ownedProject);
    }

    private void validateExpectedVersion(Project project, long expectedVersion) {
        if (project.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, project.version());
        }
    }

    private void requirePlanningInput(String projectId) {
        if (inputs.findCurrent(projectId).isEmpty()) {
            throw new IllegalArgumentException("Planning input is required");
        }
    }

    private void requireProjectIsNotProcessing(String projectId) {
        if (attempts.findProcessingByProjectId(projectId).isPresent()) {
            throw new AnalysisAlreadyProcessingException();
        }
    }

    private void requireUserCapacity(long ownerId) {
        if (attempts.findProcessingByOwnerId(ownerId).isPresent()) {
            throw new AnalysisCapacityExceededException(
                    ErrorCode.ANALYSIS_USER_LIMIT_REACHED);
        }
    }

    private void reserveServerCapacity() {
        var slot = admission.lock();
        if (slot.activeCount() >= 2) {
            throw new AnalysisCapacityExceededException(
                    ErrorCode.ANALYSIS_SERVER_LIMIT_REACHED);
        }
        admission.increment();
    }

    private AnalysisAttempt createProcessingAttempt(
            AuthenticatedPrincipal principal,
            Project project,
            String projectId,
            long generation,
            Instant startedAt) {
        return new AnalysisAttempt(
                NEW_ATTEMPT_ID,
                projectId,
                UUID.randomUUID().toString(),
                generation,
                ANALYSIS_VERSION,
                PROCESSING_STATE,
                startedAt.plusSeconds(timeoutSeconds),
                startedAt,
                null,
                principal.userId(),
                project.currentInputRevision(),
                null);
    }

    private void recordAnalysisStarted(
            AuthenticatedPrincipal principal,
            String projectId,
            Instant startedAt) {
        audit.record(new AuditEvent(
                principal.userId(),
                projectId,
                "ANALYSIS_STARTED",
                "SUCCEEDED",
                startedAt,
                null,
                java.util.Map.of("state", PROCESSING_STATE)));
    }

    @Transactional(readOnly = true)
    public AnalysisAttempt latest(
            AuthenticatedPrincipal principal,
            String projectId) {
        requireAuthenticated(principal);
        findOwnedProject(projectId, principal.userId());
        return attempts.findLatestByProjectId(projectId)
                .orElseThrow(ProjectNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public AnalysisResult latestResult(
            AuthenticatedPrincipal principal,
            String projectId) {
        requireAuthenticated(principal);
        findOwnedProject(projectId, principal.userId());
        return results.findLatestByProjectId(projectId)
                .orElseThrow(AnalysisResultNotFoundException::new);
    }

    @Transactional
    public void timeout(AnalysisAttempt attempt) {
        complete(attempt, "FAILED", "TIMEOUT", "ANALYSIS_TIMEOUT");
    }

    /** Shared idempotent terminal transition used by timeout and future worker outcomes. */
    @Transactional
    public void complete(AnalysisAttempt attempt, String terminalState, String errorCode) {
        complete(attempt, terminalState, errorCode, "ANALYSIS_COMPLETED");
    }

    private void complete(AnalysisAttempt attempt, String terminalState, String errorCode, String auditAction) {
        Instant now = Instant.now(clock);
        if (attempts.transitionToTerminal(attempt.id(), terminalState, errorCode, now)) {
            if (attempt.inputRevision() != null) {
                inputs.scrub(attempt.projectId(), attempt.inputRevision());
            }
            admission.lock();
            admission.decrement();
            audit.record(new AuditEvent(
                    attempt.ownerId() == null ? 0 : attempt.ownerId(),
                    attempt.projectId(),
                    auditAction,
                    "SUCCEEDED",
                    now,
                    errorCode,
                    java.util.Map.of("state", terminalState)));
        }
    }
}
