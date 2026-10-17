package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.repository.*;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.global.exception.*;
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
    private static final long AI_V2_MAX_SECONDS = 178L;
    private final ProjectRepository projects; private final AnalysisAttemptRepository attempts;
    private final AnalysisAdmissionRepository admission; private final PlanningInputRepository inputs;
    private final Clock clock; private final long timeoutSeconds;
    private final AuditRecorder audit;
    public ProjectAnalysisService(ProjectRepository p, AnalysisAttemptRepository a, AnalysisAdmissionRepository ad, PlanningInputRepository i, Clock c, @Value("${agentfit.analysis.timeout-seconds:600}") long timeoutSeconds) { this(p,a,ad,i,c,timeoutSeconds,new AuditRecorder()); }
    @Autowired
    public ProjectAnalysisService(ProjectRepository p, AnalysisAttemptRepository a, AnalysisAdmissionRepository ad, PlanningInputRepository i, Clock c, @Value("${agentfit.analysis.timeout-seconds:600}") long timeoutSeconds, AuditRecorder audit) { projects=p;attempts=a;admission=ad;inputs=i;clock=c;this.timeoutSeconds=timeoutSeconds;this.audit=audit; }
    @Transactional
    public AnalysisAttempt start(AuthenticatedPrincipal principal,String projectId,long expectedVersion) {
        if(principal==null) throw new UnauthenticatedException();
        var project = principal.userUuid() == null
            ? projects.findByIdAndOwnerId(projectId, principal.userId()).orElseThrow(ProjectNotFoundException::new)
            : projects.findByIdAndOwnerUuid(projectId, principal.userUuid()).orElseThrow(ProjectNotFoundException::new);
        var locked=projects.findByIdForUpdate(projectId).orElse(project);
        if(locked.version()!=expectedVersion) throw new VersionConflictException(expectedVersion,locked.version());
        if(inputs.findCurrent(projectId).isEmpty()) throw new IllegalArgumentException("Planning input is required");
        if(attempts.findProcessingByProjectId(projectId).isPresent()) throw new AnalysisAlreadyProcessingException();
        boolean userHasProcessing = principal.userUuid() == null
            ? attempts.findProcessingByOwnerId(principal.userId()).isPresent()
            : attempts.findProcessingByOwnerUuid(principal.userUuid()).isPresent();
        if(userHasProcessing) throw new AnalysisCapacityExceededException(ErrorCode.ANALYSIS_USER_LIMIT_REACHED);
        var slot=admission.lock(); if(slot.activeCount()>=2) throw new AnalysisCapacityExceededException(ErrorCode.ANALYSIS_SERVER_LIMIT_REACHED);
        admission.increment(); Instant now=Instant.now(clock); long generation=locked.analysisGeneration()+1;
        long executionSeconds = Math.min(timeoutSeconds, AI_V2_MAX_SECONDS);
        if (executionSeconds < 1) throw new IllegalArgumentException("analysis timeout must be positive");
        AnalysisAttempt attempt=new AnalysisAttempt(UUID.randomUUID().toString(),projectId,UUID.randomUUID().toString(),generation,"1.0","PROCESSING",now.plusSeconds(executionSeconds),now,null,principal.userUuid(),locked.currentInputRevision(),null,null,null,null,locked.version()+1,"planning-input:" + locked.currentInputRevision());
        AnalysisAttempt saved=attempts.start(attempt); projects.markAnalysisStarted(projectId,generation,locked.version()+1,now);
        if (principal.userUuid() != null) audit.record(new AuditEvent(principal.userUuid(), UUID.fromString(projectId), "ANALYSIS_STARTED", "SUCCEEDED", now, null, java.util.Map.of("state", "PROCESSING")));
        else audit.record(new AuditEvent(principal.userId(),projectId,"ANALYSIS_STARTED","SUCCEEDED",now,null,java.util.Map.of("state","PROCESSING")));
        return saved;
    }
    @Transactional
    public void timeout(AnalysisAttempt attempt) { complete(attempt, "FAILED", "TIMEOUT", "ANALYSIS_TIMEOUT"); }

    /** Shared idempotent terminal transition used by timeout and future worker outcomes. */
    @Transactional
    public void complete(AnalysisAttempt attempt, String terminalState, String errorCode) {
        complete(attempt, terminalState, errorCode, "ANALYSIS_COMPLETED");
    }

    private void complete(AnalysisAttempt attempt, String terminalState, String errorCode, String auditAction) {
        Instant now=Instant.now(clock);
        if (attempts.transitionToTerminal(attempt.id(), terminalState, errorCode, now)) {
            if (attempt.inputRevision()!=null) inputs.scrub(attempt.projectId(), attempt.inputRevision());
            admission.lock(); admission.decrement();
            UUID projectUuid = uuidOrNull(attempt.projectId());
            if (attempt.ownerUuid() != null && projectUuid != null) audit.record(new AuditEvent(attempt.ownerUuid(), projectUuid, auditAction, "SUCCEEDED", now, errorCode, java.util.Map.of("state", terminalState)));
            else audit.record(new AuditEvent(attempt.ownerId() == null ? 0L : attempt.ownerId(), attempt.projectId(), auditAction, "SUCCEEDED", now, errorCode, java.util.Map.of("state", terminalState)));
        }
    }

    private UUID uuidOrNull(String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
