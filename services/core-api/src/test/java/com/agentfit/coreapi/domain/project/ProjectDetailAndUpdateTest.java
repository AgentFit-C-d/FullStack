package com.agentfit.coreapi.domain.project;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.dto.request.ProjectUpdateRequest;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.global.exception.VersionConflictException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectDetailAndUpdateTest {
    @Test void detailRestoresRoleRuntimeAndInputMetadataAndUpdateIncrementsVersion() {
        var repo = new Repo();
        var runtimes = new Runtimes();
        var inputs = new Inputs();
        var project = new Project("p1", 7, "old", 1, "CREATED", 0, 0, Instant.now(), Instant.now(), "BACKEND", 1L);
        repo.current = project;
        runtimes.values = List.of(new ProjectRuntime("p1", 1, "Java", "java"), new ProjectRuntime("p1", 2, "Spring", "spring"));
        inputs.current = new PlanningInput("p1", 1, "MARKDOWN", "plan.md", "text/markdown", 4L, 4, null, null, null, null, Instant.now());
        var service = new ProjectService(repo, new ProjectOwnershipGuard(repo), new com.agentfit.coreapi.domain.audit.service.AuditRecorder(), runtimes, inputs, new ProjectCreationIdempotencyRepository(){}, new PlanningInputValidator(), new AnalysisAttemptRepository(){});

        var detail = service.detail(new AuthenticatedPrincipal(7, "user", null), "p1");
        assertEquals("BACKEND", detail.project().role());
        assertEquals(List.of("Java", "Spring"), detail.project().runtimes());
        assertEquals("MARKDOWN", ((com.agentfit.coreapi.domain.project.dto.response.PlanningInputMetadataResponse) detail.confirmed()).inputType());

        service.update(new AuthenticatedPrincipal(7, "user", null), "p1", new ProjectUpdateRequest("new", "AI", "Python, python, FastAPI", 1));
        assertEquals(2, repo.current.version());
        assertEquals("AI", repo.current.developerRole());
        assertEquals("new", repo.current.name());
        assertEquals(List.of("Python", "FastAPI"), runtimes.values.stream().map(ProjectRuntime::value).toList());
    }

    @Test void staleVersionIsRejectedBeforeMutation() {
        var repo = new Repo();
        repo.current = new Project("p1", 7, "old", 3, Instant.now(), Instant.now());
        var service = new ProjectService(repo);
        assertThrows(VersionConflictException.class, () -> service.update(new AuthenticatedPrincipal(7, "user", null), "p1", new ProjectUpdateRequest("new", "BACKEND", "Java", 2)));
        assertEquals("old", repo.current.name());
    }

    static class Repo implements ProjectRepository {
        Project current;
        public Project save(Project p) { current = p; return p; }
        public List<Project> findByOwnerId(long owner) { return current == null ? List.of() : List.of(current); }
        public Optional<Project> findByIdAndOwnerId(String id, long owner) { return Optional.ofNullable(current).filter(p -> p.id().equals(id) && p.ownerId() == owner); }
        public Optional<Project> findByIdForUpdate(String id) { return Optional.ofNullable(current).filter(p -> p.id().equals(id)); }
        public void updateMetadata(String id, String name, String role, long version, Instant updatedAt) { current = new Project(id, current.ownerId(), name, version, current.stage(), current.analysisGeneration(), current.reviewGeneration(), current.createdAt(), updatedAt, role, current.currentInputRevision()); }
    }
    static class Runtimes implements ProjectRuntimeRepository { List<ProjectRuntime> values = List.of(); public void replace(String id, List<ProjectRuntime> v) { values = List.copyOf(v); } public List<ProjectRuntime> findByProjectId(String id) { return values; } }
    static class Inputs implements PlanningInputRepository { PlanningInput current; public Optional<PlanningInput> findCurrent(String id) { return Optional.ofNullable(current); } }
}
