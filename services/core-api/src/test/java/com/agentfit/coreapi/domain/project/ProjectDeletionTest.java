package com.agentfit.coreapi.domain.project;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.global.exception.VersionConflictException;

class ProjectDeletionTest {
    @Test
    void staleDeleteDoesNotCallRepositoryDelete() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository)
            .create(new AuthenticatedPrincipal(7L, "owner", null), "demo");
        ProjectDeletionService service = new ProjectDeletionService(new ProjectOwnershipGuard(repository), repository);

        assertThrows(VersionConflictException.class, () -> service.delete(
            new AuthenticatedPrincipal(7L, "owner", null), project.id(), true, 99L));
        if (repository.projects.isEmpty()) {
            throw new AssertionError("stale delete removed the project");
        }
    }

    @Test
    void unconfirmedAndForeignDeleteAreRejected() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository)
            .create(new AuthenticatedPrincipal(7L, "owner", null), "demo");
        ProjectDeletionService service = new ProjectDeletionService(new ProjectOwnershipGuard(repository), repository);

        assertThrows(IllegalArgumentException.class, () -> service.delete(
            new AuthenticatedPrincipal(7L, "owner", null), project.id(), false, 1L));
        assertThrows(ProjectNotFoundException.class, () -> service.delete(
            new AuthenticatedPrincipal(8L, "other", null), project.id(), true, 1L));
    }
}



