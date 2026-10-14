package com.agentfit.coreapi.contract;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.domain.project.repository.ProjectRepository;

class AccessIsolationContractTest {
    @Test
    void anonymousProjectReadIs401() {
        ProjectOwnershipGuard guard = new ProjectOwnershipGuard(new SingleProjectRepository());
        assertThrows(UnauthenticatedException.class, () -> guard.requireOwned(null, "p-1"));
    }

    @Test
    void foreignAndUnknownProjectReadsAreTheSame404() {
        ProjectOwnershipGuard guard = new ProjectOwnershipGuard(new SingleProjectRepository());
        AuthenticatedPrincipal other = new AuthenticatedPrincipal(99L, "other", null);
        assertThrows(ProjectNotFoundException.class, () -> guard.requireOwned(other, "p-1"));
        assertThrows(ProjectNotFoundException.class, () -> guard.requireOwned(other, "missing"));
    }

    static final class SingleProjectRepository implements ProjectRepository {
        @Override public Project save(Project project) { return project; }
        @Override public java.util.List<Project> findByOwnerId(long ownerId) { return java.util.List.of(); }
        @Override public Optional<Project> findByIdAndOwnerId(String id, long ownerId) {
            if (id.equals("p-1") && ownerId == 7L) {
                Instant now = Instant.parse("2026-10-08T00:00:00Z");
                return Optional.of(new Project("p-1", 7L, "owned", 1L, now, now));
            }
            return Optional.empty();
        }
    }
}



