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

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.global.exception.VersionConflictException;

/** Reusable owner + base-version boundary for future profile/analysis writes. */
public class ProjectWriteGuard {
    private final ProjectOwnershipGuard ownershipGuard;

    public ProjectWriteGuard(ProjectOwnershipGuard ownershipGuard) {
        this.ownershipGuard = ownershipGuard;
    }

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
}



