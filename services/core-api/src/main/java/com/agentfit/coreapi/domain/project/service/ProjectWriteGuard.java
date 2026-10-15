package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.util.function.Function;
import org.springframework.transaction.annotation.Transactional;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.global.exception.VersionConflictException;

/** Reusable owner + base-version boundary for future profile/analysis writes. */
public class ProjectWriteGuard {
    private final ProjectOwnershipGuard ownershipGuard;
    private final ProjectRepository repository;
    private final com.agentfit.coreapi.domain.audit.service.AuditRecorder auditRecorder;

    public ProjectWriteGuard(ProjectOwnershipGuard ownershipGuard) {
        this(ownershipGuard, null, new com.agentfit.coreapi.domain.audit.service.AuditRecorder());
    }

    public ProjectWriteGuard(ProjectOwnershipGuard ownershipGuard, ProjectRepository repository,
            com.agentfit.coreapi.domain.audit.service.AuditRecorder auditRecorder) {
        this.ownershipGuard = ownershipGuard;
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public <T> T execute(AuthenticatedPrincipal principal, ProjectWriteCommand command,
            Function<Project, T> write) {
        Project project = ownershipGuard.requireOwned(principal, command.projectId());
        synchronized (project.id().intern()) {
            if (project.version() != command.baseVersion()) {
                throw new VersionConflictException(command.baseVersion(), project.version());
            }
            return write.apply(project);
        }
    }

    /** Executes a protected mutation and atomically advances the project version. */
    @Transactional
    public <T> T executeAtomic(AuthenticatedPrincipal principal, ProjectWriteCommand command,
            String action, Function<Project, T> write) {
        Project project = ownershipGuard.requireOwned(principal, command.projectId());
        synchronized (project.id().intern()) {
            if (project.version() != command.baseVersion()) {
                throw new VersionConflictException(command.baseVersion(), project.version());
            }
            T result = write.apply(project);
            if (repository != null) {
                repository.updateVersion(project.id(), project.version() + 1, java.time.Instant.now());
            }
            auditRecorder.record(new com.agentfit.coreapi.domain.audit.entity.AuditEvent(
                principal.userId(), project.id(), action, "SUCCEEDED", java.time.Instant.now(), null,
                java.util.Map.of("target", "PROJECT")));
            return result;
        }
    }
}



