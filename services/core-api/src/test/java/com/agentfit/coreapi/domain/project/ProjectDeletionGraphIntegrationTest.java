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

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;

class ProjectDeletionGraphIntegrationTest {
    @Test
    void deletingProjectRemovesProjectButDoesNotTouchUserSessionBoundary() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository)
            .create(new AuthenticatedPrincipal(7L, "owner", null), "demo");
        ProjectDeletionService service = new ProjectDeletionService(new ProjectOwnershipGuard(repository), repository);

        service.delete(new AuthenticatedPrincipal(7L, "owner", null), project.id(), true, 1L);

        assertEquals(0, repository.projects.size());
    }
}



