package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;

/** Common owner boundary for every project-scoped operation. */
@org.springframework.stereotype.Component
public class ProjectOwnershipGuard {
    private final ProjectRepository repository;

    public ProjectOwnershipGuard(ProjectRepository repository) {
        this.repository = repository;
    }

    public Project requireOwned(AuthenticatedPrincipal principal, String projectId) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        if (principal.userUuid() != null) {
            return repository.findByIdAndOwnerUuid(projectId, principal.userUuid())
                .orElseThrow(ProjectNotFoundException::new);
        }
        return repository.findByIdAndOwnerId(projectId, principal.userId())
            .orElseThrow(ProjectNotFoundException::new);
    }
}



