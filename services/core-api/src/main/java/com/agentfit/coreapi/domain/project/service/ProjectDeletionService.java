package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.global.exception.VersionConflictException;

@Service
public class ProjectDeletionService {
    private final ProjectOwnershipGuard ownershipGuard;
    private final ProjectRepository repository;
    private final com.agentfit.coreapi.domain.audit.service.AuditRecorder auditRecorder;

    public ProjectDeletionService(ProjectOwnershipGuard ownershipGuard, ProjectRepository repository) {
        this(ownershipGuard, repository, new com.agentfit.coreapi.domain.audit.service.AuditRecorder());
    }

    @Autowired
    public ProjectDeletionService(ProjectOwnershipGuard ownershipGuard, ProjectRepository repository,
            com.agentfit.coreapi.domain.audit.service.AuditRecorder auditRecorder) {
        this.ownershipGuard = ownershipGuard;
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public void delete(AuthenticatedPrincipal principal, String projectId, boolean confirmation, long expectedVersion) {
        if (!confirmation) {
            throw new IllegalArgumentException("confirmation must be true");
        }
        Project project = ownershipGuard.requireOwned(principal, projectId);
        Project locked = repository.findByIdForUpdate(projectId).orElse(project);
        if (locked.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, locked.version());
        }
        auditRecorder.record(new com.agentfit.coreapi.domain.audit.entity.AuditEvent(
            principal.userId(), locked.id(), "PROJECT_DELETED", "SUCCEEDED", java.time.Instant.now(), null,
            java.util.Map.of("target", "PROJECT")));
        // The project FK cascade removes this project-scoped audit row in the same transaction.
        repository.deleteProjectGraph(locked.id());
    }
}



