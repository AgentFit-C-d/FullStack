package com.agentfit.coreapi.domain.project;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class PlanningInputRevisionTest {
    @Test void replacementCreatesNewRevisionAndKeepsPreviousMetadata() {
        var repo = new Repo();
        var inputs = new Inputs();
        inputs.values.add(new PlanningInput("p1", 1, "TEXT", null, "text/plain", 3L, 3, null, null, null, null, Instant.now()));
        repo.current = new Project("p1", 7, "demo", 1, "CREATED", 0, 0, Instant.now(), Instant.now(), "BACKEND", 1L);
        var service = new ProjectService(repo, new ProjectOwnershipGuard(repo), new com.agentfit.coreapi.domain.audit.service.AuditRecorder(), new ProjectRuntimeRepository(){}, inputs, new ProjectCreationIdempotencyRepository(){}, new PlanningInputValidator(), new AnalysisAttemptRepository(){});

        var metadata = service.replaceInput(new AuthenticatedPrincipal(7, "user", null), "p1", 1,
                new MockMultipartFile("file", "plan.md", "text/markdown", "new plan".getBytes()), null);
        assertEquals(2, metadata.revision());
        assertEquals(2, repo.current.version());
        assertEquals(2, repo.current.currentInputRevision());
        assertEquals(2, inputs.values.size());
        assertEquals(1, inputs.values.get(0).revision());
    }

    static class Repo implements ProjectRepository {
        Project current;
        public Project save(Project p) { current = p; return p; }
        public List<Project> findByOwnerId(long o) { return List.of(current); }
        public Optional<Project> findByIdAndOwnerId(String id, long o) { return Optional.ofNullable(current).filter(p -> p.id().equals(id) && p.ownerId() == o); }
        public Optional<Project> findByIdForUpdate(String id) { return Optional.ofNullable(current); }
        public void updateInputRevision(String id, long revision, long version, Instant updatedAt) { current = new Project(id,current.ownerId(),current.name(),version,current.stage(),current.analysisGeneration(),current.reviewGeneration(),current.createdAt(),updatedAt,current.developerRole(),revision); }
    }
    static class Inputs implements PlanningInputRepository {
        List<PlanningInput> values = new ArrayList<>();
        public void save(PlanningInput input) { values.add(input); }
        public Optional<PlanningInput> findCurrent(String id) { return values.stream().max(Comparator.comparingLong(PlanningInput::revision)); }
    }
}
