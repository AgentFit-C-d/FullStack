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
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import java.time.Instant;
import java.util.Optional;
import java.util.ArrayList;

class ProjectDeletionTest {
    @Test
    void deletesBDataBeforeAProjectGraph() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository)
            .create(new AuthenticatedPrincipal(7L, "owner", null), "demo");
        ArrayList<String> calls = new ArrayList<>();
        ProjectService service = new ProjectService(repository, new ProjectOwnershipGuard(repository),
            new com.agentfit.coreapi.domain.audit.service.AuditRecorder(), new ProjectRuntimeRepository(){},
            new PlanningInputRepository(){}, new ProjectCreationIdempotencyRepository(){},
            new PlanningInputValidator(), new AnalysisAttemptRepository(){},
            id -> calls.add("B:" + id));

        service.delete(new AuthenticatedPrincipal(7L, "owner", null), project.id(), true, 1L);

        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("B:" + project.id()), calls);
        org.junit.jupiter.api.Assertions.assertTrue(repository.projects.isEmpty());
    }

    @Test
    void staleDeleteDoesNotCallRepositoryDelete() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository)
            .create(new AuthenticatedPrincipal(7L, "owner", null), "demo");
        ProjectService service = new ProjectService(repository);

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
        ProjectService service = new ProjectService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.delete(
            new AuthenticatedPrincipal(7L, "owner", null), project.id(), false, 1L));
        assertThrows(ProjectNotFoundException.class, () -> service.delete(
            new AuthenticatedPrincipal(8L, "other", null), project.id(), true, 1L));
    }

    @Test
    void activeAnalysisDeletionIsRejected() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository).create(new AuthenticatedPrincipal(7L, "owner", null), "demo");
        AnalysisAttemptRepository active = new AnalysisAttemptRepository() {
            public Optional<AnalysisAttempt> findProcessingByProjectId(String id) {
                return Optional.of(new AnalysisAttempt(1, id, "r", 1, "1", "PROCESSING", Instant.now().plusSeconds(10), Instant.now(), null));
            }
        };
        ProjectService service = new ProjectService(repository, new ProjectOwnershipGuard(repository), new com.agentfit.coreapi.domain.audit.service.AuditRecorder(), new ProjectRuntimeRepository(){}, new PlanningInputRepository(){}, new ProjectCreationIdempotencyRepository(){}, new PlanningInputValidator(), active);
        assertThrows(ActiveAnalysisProjectDeletionException.class, () -> service.delete(new AuthenticatedPrincipal(7L, "owner", null), project.id(), true, 1L));
    }
}



