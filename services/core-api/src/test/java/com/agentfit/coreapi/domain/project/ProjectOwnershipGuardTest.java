package com.agentfit.coreapi.domain.project;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;

class ProjectOwnershipGuardTest {
    @Test
    void ownedProjectIsReturnedAndForeignProjectIsNotMutated() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository)
            .create(new AuthenticatedPrincipal(7L, "owner", null), "owned");
        ProjectOwnershipGuard guard = new ProjectOwnershipGuard(repository);

        assertEquals(project.id(), guard.requireOwned(new AuthenticatedPrincipal(7L, "owner", null), project.id()).id());
        assertThrows(ProjectNotFoundException.class,
            () -> guard.requireOwned(new AuthenticatedPrincipal(8L, "other", null), project.id()));
        assertEquals(1, repository.projects.size());
    }
}



