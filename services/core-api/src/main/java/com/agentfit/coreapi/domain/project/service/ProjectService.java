package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;

@Service
public class ProjectService {
    private final ProjectRepository repository;
    private final ProjectOwnershipGuard ownershipGuard;
    private final com.agentfit.coreapi.domain.audit.service.AuditRecorder auditRecorder;

    public ProjectService(ProjectRepository repository) {
        this(repository, new ProjectOwnershipGuard(repository), new com.agentfit.coreapi.domain.audit.service.AuditRecorder());
    }

    public ProjectService(ProjectRepository repository,
            com.agentfit.coreapi.domain.audit.service.AuditRecorder auditRecorder) {
        this(repository, new ProjectOwnershipGuard(repository), auditRecorder);
    }

    public ProjectService(ProjectRepository repository,
            ProjectOwnershipGuard ownershipGuard,
            com.agentfit.coreapi.domain.audit.service.AuditRecorder auditRecorder) {
        this.repository = repository;
        this.ownershipGuard = ownershipGuard;
        this.auditRecorder = auditRecorder;
    }

    @Autowired
    public ProjectService(ProjectRepository repository, ProjectOwnershipGuard ownershipGuard) {
        this(repository, ownershipGuard, new com.agentfit.coreapi.domain.audit.service.AuditRecorder());
    }

    @Transactional
    public Project create(AuthenticatedPrincipal principal, String rawName) {
        if (principal == null) {
            throw new com.agentfit.coreapi.global.exception.UnauthenticatedException();
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 100) {
            throw new IllegalArgumentException("Project name must contain 1 to 100 code points");
        }
        Instant now = Instant.now();
        Project project = new Project(java.util.UUID.randomUUID().toString(), principal.userId(), name, 1L, now, now);
        Project saved = repository.save(project);
        auditRecorder.record(new com.agentfit.coreapi.domain.audit.entity.AuditEvent(
            principal.userId(), saved.id(), "PROJECT_CREATED", "SUCCEEDED", now, null,
            java.util.Map.of("target", "PROJECT")));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Project> list(AuthenticatedPrincipal principal) {
        return repository.findByOwnerId(principal.userId());
    }

    @Transactional(readOnly = true)
    public Project get(AuthenticatedPrincipal principal, String projectId) {
        return ownershipGuard.requireOwned(principal, projectId);
    }
}



