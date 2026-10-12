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

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.global.exception.VersionConflictException;

@Service
public class ProjectDeletionService {
    private final ProjectOwnershipGuard ownershipGuard;
    private final ProjectRepository repository;

    public ProjectDeletionService(ProjectOwnershipGuard ownershipGuard, ProjectRepository repository) {
        this.ownershipGuard = ownershipGuard;
        this.repository = repository;
    }

    @Transactional
    public void delete(AuthenticatedPrincipal principal, String projectId, boolean confirmation, long expectedVersion) {
        if (!confirmation) {
            throw new IllegalArgumentException("confirmation must be true");
        }
        Project project = ownershipGuard.requireOwned(principal, projectId);
        if (project.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, project.version());
        }
        repository.deleteById(project.id());
    }
}



