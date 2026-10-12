package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;

@Service
public class ProjectQueryService {
    private final ProjectOwnershipGuard ownershipGuard;
    private final ProjectRepository repository;

    public ProjectQueryService(ProjectOwnershipGuard ownershipGuard, ProjectRepository repository) {
        this.ownershipGuard = ownershipGuard;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Project get(AuthenticatedPrincipal principal, String projectId) {
        return ownershipGuard.requireOwned(principal, projectId);
    }

    @Transactional(readOnly = true)
    public List<Project> list(AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        return repository.findByOwnerId(principal.userId());
    }
}



