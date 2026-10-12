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

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
        this.ownershipGuard = new ProjectOwnershipGuard(repository);
    }

    @Autowired
    public ProjectService(ProjectRepository repository, ProjectOwnershipGuard ownershipGuard) {
        this.repository = repository;
        this.ownershipGuard = ownershipGuard;
    }

    @Transactional
    public Project create(AuthenticatedPrincipal principal, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 100) {
            throw new IllegalArgumentException("Project name must contain 1 to 100 code points");
        }
        Instant now = Instant.now();
        Project project = new Project(java.util.UUID.randomUUID().toString(), principal.userId(), name, 1L, now, now);
        return repository.save(project);
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



