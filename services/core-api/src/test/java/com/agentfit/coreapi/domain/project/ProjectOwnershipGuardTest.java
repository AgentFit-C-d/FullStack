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
import java.util.UUID;
import java.util.List;

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

    @Test
    void uuidPrincipalUsesUuidOwnershipLookup() {
        UUID ownerUuid = UUID.randomUUID();
        Project project = new Project("p-uuid", 0L, ownerUuid, "owned", 1L,
            java.time.Instant.now(), java.time.Instant.now());
        ProjectRepository repository = new ProjectRepository() {
            @Override public Project save(Project value) { return value; }
            @Override public List<Project> findByOwnerId(long ownerId) { return List.of(); }
            @Override public Optional<Project> findByIdAndOwnerId(String id, long ownerId) { return Optional.empty(); }
            @Override public Optional<Project> findByIdAndOwnerUuid(String id, UUID uuid) {
                return ownerUuid.equals(uuid) && project.id().equals(id) ? Optional.of(project) : Optional.empty();
            }
        };

        Project result = new ProjectOwnershipGuard(repository)
            .requireOwned(new AuthenticatedPrincipal(99L, "owner", null, ownerUuid), "p-uuid");

        assertEquals(ownerUuid, result.ownerUuid());
    }
}



