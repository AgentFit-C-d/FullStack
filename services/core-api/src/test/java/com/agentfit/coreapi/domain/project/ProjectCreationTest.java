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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;

class ProjectCreationTest {
    @Test
    void serverPrincipalOwnsCreatedProjectAndNameIsTrimmed() {
        InMemoryProjectRepository repository = new InMemoryProjectRepository();
        ProjectService service = new ProjectService(repository);

        Project project = service.create(new AuthenticatedPrincipal(7L, "Alice", null), "  My project  ");

        assertEquals(7L, project.ownerId());
        assertEquals("My project", project.name());
        assertEquals(1L, project.version());
    }

    @Test
    void blankNameIsRejected() {
        ProjectService service = new ProjectService(new InMemoryProjectRepository());
        assertThrows(IllegalArgumentException.class,
            () -> service.create(new AuthenticatedPrincipal(7L, "Alice", null), " \t "));
    }

    static final class InMemoryProjectRepository implements ProjectRepository {
        final List<Project> projects = new ArrayList<>();

        @Override
        public Project save(Project project) {
            projects.add(project);
            return project;
        }

        @Override
        public List<Project> findByOwnerId(long ownerId) {
            return projects.stream().filter(p -> p.ownerId() == ownerId).toList();
        }

        @Override
        public java.util.Optional<Project> findByIdAndOwnerId(String id, long ownerId) {
            return projects.stream().filter(p -> p.id().equals(id) && p.ownerId() == ownerId).findFirst();
        }

        @Override
        public void deleteById(String id) {
            projects.removeIf(project -> project.id().equals(id));
        }
    }
}



