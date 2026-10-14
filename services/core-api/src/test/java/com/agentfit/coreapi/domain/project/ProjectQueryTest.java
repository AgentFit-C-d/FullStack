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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;

class ProjectQueryTest {
    @Test
    void listIsRestrictedToAuthenticatedOwner() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        ProjectService service = new ProjectService(repository);
        Project own = service.create(new AuthenticatedPrincipal(1L, "one", null), "one");
        service.create(new AuthenticatedPrincipal(2L, "two", null), "two");

        List<Project> result = service.list(new AuthenticatedPrincipal(1L, "one", null));

        assertEquals(List.of("one"), result.stream().map(Project::name).toList());
        assertEquals("one", service.get(new AuthenticatedPrincipal(1L, "one", null), own.id()).name());
        assertThrows(ProjectNotFoundException.class,
            () -> service.get(new AuthenticatedPrincipal(2L, "two", null), own.id()));
    }
}



