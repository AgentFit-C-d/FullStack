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

class ProjectAccessWebMvcTest {
    @Test
    void directProjectLookupDoesNotReturnForeignData() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository)
            .create(new AuthenticatedPrincipal(7L, "owner", null), "owned");
        ProjectQueryService queryService = new ProjectQueryService(new ProjectOwnershipGuard(repository), repository);

        assertThrows(ProjectNotFoundException.class,
            () -> queryService.get(new AuthenticatedPrincipal(8L, "other", null), project.id()));
    }

    @Test
    void unknownProjectHasTheSameHiddenNotFoundMeaning() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        ProjectQueryService queryService = new ProjectQueryService(new ProjectOwnershipGuard(repository), repository);
        assertThrows(ProjectNotFoundException.class,
            () -> queryService.get(new AuthenticatedPrincipal(7L, "owner", null), "unknown"));
    }
}



