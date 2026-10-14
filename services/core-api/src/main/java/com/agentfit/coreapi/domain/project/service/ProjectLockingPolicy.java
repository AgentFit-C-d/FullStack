package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.util.function.Supplier;

import com.agentfit.coreapi.global.exception.VersionConflictException;

/** Shared write boundary: lock PROJECT first, then compare the caller's base version. */
public final class ProjectLockingPolicy {
    private final LockedProjectRepository repository;

    public ProjectLockingPolicy(LockedProjectRepository repository) {
        this.repository = repository;
    }

    public <T> T execute(long projectId, long expectedVersion, Supplier<T> write) {
        LockedProject project = repository.lockById(projectId);
        if (project == null) {
            throw new com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException();
        }
        if (project.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, project.version());
        }
        return write.get();
    }

    public void executeVoid(long projectId, long expectedVersion, Runnable write) {
        execute(projectId, expectedVersion, () -> {
            write.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface LockedProjectRepository {
        LockedProject lockById(long projectId);
    }

    public record LockedProject(long id, long version) {
    }
}



