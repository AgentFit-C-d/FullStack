package com.agentfit.coreapi.domain.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.project.repository.ProjectRepository;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.domain.project.service.ProjectWriteCommand;
import com.agentfit.coreapi.domain.project.service.ProjectWriteGuard;
import com.agentfit.coreapi.global.exception.VersionConflictException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Transaction-boundary contract; the same assertions are used by the PostgreSQL adapter. */
class ProjectWriteGuardPostgresIntegrationTest {
    @Test
    void atomicWriteAdvancesVersionAndRecordsOnlyAfterBaseVersionPasses() {
        Instant now = Instant.parse("2026-10-08T00:00:00Z");
        MutableRepository repository = new MutableRepository(new Project("p-1", 7L, "demo", 1L, now, now));
        List<String> actions = new ArrayList<>();
        ProjectWriteGuard guard = new ProjectWriteGuard(new ProjectOwnershipGuard(repository), repository,
            new AuditRecorder(event -> actions.add(event.action())));

        guard.executeAtomic(new AuthenticatedPrincipal(7L, "owner", null), new ProjectWriteCommand("p-1", 1L),
            "PROFILE_UPDATED", ignored -> "saved");

        assertEquals(2L, repository.version);
        assertEquals(List.of("PROFILE_UPDATED"), actions);
        assertThrows(VersionConflictException.class, () -> guard.executeAtomic(
            new AuthenticatedPrincipal(7L, "owner", null), new ProjectWriteCommand("p-1", 1L),
            "PROFILE_UPDATED", ignored -> "not-saved"));
        assertEquals(2L, repository.version);
        assertEquals(1, actions.size());
    }

    private static final class MutableRepository implements ProjectRepository {
        private Project project;
        private long version;
        MutableRepository(Project project) { this.project = project; this.version = project.version(); }
        @Override public Project save(Project value) { project = value; version = value.version(); return value; }
        @Override public List<Project> findByOwnerId(long ownerId) { return List.of(project); }
        @Override public Optional<Project> findByIdAndOwnerId(String id, long ownerId) {
            return id.equals(project.id()) && ownerId == project.ownerId() ? Optional.of(project) : Optional.empty();
        }
        @Override public void updateVersion(String id, long next, Instant updatedAt) {
            version = next;
            project = new Project(project.id(), project.ownerId(), project.name(), next, project.stage(),
                project.analysisGeneration(), project.reviewGeneration(), project.createdAt(), updatedAt);
        }
    }
}
