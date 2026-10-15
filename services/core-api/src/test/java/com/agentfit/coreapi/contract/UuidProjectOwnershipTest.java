package com.agentfit.coreapi.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.repository.ProjectRepository;
import com.agentfit.coreapi.domain.project.service.ProjectService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidProjectOwnershipTest {
    @Test
    void projectCarriesCanonicalUuidOwnerAlongsideLegacyTestIdentity() {
        UUID owner = UUID.randomUUID();
        Project project = new Project(UUID.randomUUID().toString(), 7L, owner, "demo", 1L,
            Instant.now(), Instant.now());

        assertEquals(owner, project.ownerUuid());
    }

    @Test
    void projectCreationCopiesAuthenticatedUuidOwner() {
        UUID owner = UUID.randomUUID();
        Project project = new ProjectService(new Repository()).create(
            new AuthenticatedPrincipal(7L, "owner", Instant.MAX, owner), "demo");

        assertEquals(owner, project.ownerUuid());
    }

    @Test
    void projectListUsesCanonicalUuidOwnerLookup() {
        UUID owner = UUID.randomUUID();
        Repository repository = new Repository();
        Project project = new Project(UUID.randomUUID().toString(), 7L, owner, "demo", 1L,
            Instant.now(), Instant.now());
        repository.projects.add(project);

        assertEquals(List.of(project), new ProjectService(repository).list(
            new AuthenticatedPrincipal(7L, "owner", Instant.MAX, owner)));
        assertEquals(owner, repository.lastUuidOwner);
    }

    private static final class Repository implements ProjectRepository {
        private final List<Project> projects = new ArrayList<>();
        private UUID lastUuidOwner;
        @Override public Project save(Project project) { projects.add(project); return project; }
        @Override public List<Project> findByOwnerId(long ownerId) { return projects; }
        @Override public java.util.Optional<Project> findByIdAndOwnerId(String id, long ownerId) {
            return projects.stream().filter(project -> project.id().equals(id)).findFirst();
        }
        @Override public List<Project> findByOwnerUuid(UUID ownerUuid) {
            lastUuidOwner = ownerUuid;
            return projects.stream().filter(project -> ownerUuid.equals(project.ownerUuid())).toList();
        }
    }
}
