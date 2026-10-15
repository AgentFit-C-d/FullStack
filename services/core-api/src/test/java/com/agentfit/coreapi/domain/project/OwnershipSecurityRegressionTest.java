package com.agentfit.coreapi.domain.project;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.domain.project.service.ProjectService;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import org.junit.jupiter.api.Test;

class OwnershipSecurityRegressionTest {
    @Test
    void directLookupAndIdGuessingDoNotBypassOwnership() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        var project = new ProjectService(repository).create(new AuthenticatedPrincipal(7L, "owner", null), "owned");
        ProjectOwnershipGuard guard = new ProjectOwnershipGuard(repository);
        assertThrows(UnauthenticatedException.class, () -> guard.requireOwned(null, project.id()));
        assertThrows(ProjectNotFoundException.class, () -> guard.requireOwned(
            new AuthenticatedPrincipal(8L, "other", null), project.id()));
        assertThrows(ProjectNotFoundException.class, () -> guard.requireOwned(
            new AuthenticatedPrincipal(7L, "owner", null), "1"));
    }
}
