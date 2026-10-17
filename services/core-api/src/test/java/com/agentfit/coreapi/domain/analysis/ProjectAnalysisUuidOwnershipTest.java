package com.agentfit.coreapi.domain.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAdmission;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAdmissionRepository;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.analysis.service.ProjectAnalysisService;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.project.repository.PlanningInputRepository;
import com.agentfit.coreapi.domain.project.repository.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectAnalysisUuidOwnershipTest {

    @Test
    void startsAnalysisThroughUuidOwnerLookup() {
        UUID ownerId = UUID.randomUUID();
        String projectId = UUID.randomUUID().toString();
        Project project = new Project(projectId, 0L, ownerId, "demo", 1L,
            Instant.parse("2026-10-17T00:00:00Z"), Instant.parse("2026-10-17T00:00:00Z"));
        ProjectRepository projects = new ProjectRepository() {
            @Override public Project save(Project value) { return value; }
            @Override public java.util.List<Project> findByOwnerId(long ignored) { return java.util.List.of(); }
            @Override public Optional<Project> findByIdAndOwnerId(String id, long ignored) { return Optional.empty(); }
            @Override public Optional<Project> findByIdAndOwnerUuid(String id, UUID owner) {
                return projectId.equals(id) && ownerId.equals(owner) ? Optional.of(project) : Optional.empty();
            }
            @Override public Optional<Project> findByIdForUpdate(String id) { return Optional.of(project); }
        };
        AnalysisAttemptRepository attempts = new AnalysisAttemptRepository() {
            @Override public AnalysisAttempt start(AnalysisAttempt attempt) { return attempt; }
        };
        PlanningInputRepository inputs = new PlanningInputRepository() {
            @Override public Optional<PlanningInput> findCurrent(String id) {
                return Optional.of(new PlanningInput(id, 1L, "TEXT", null, "text/plain", 4L, 4, null,
                    "demo".getBytes(), "demo", null, Instant.parse("2026-10-17T00:00:00Z")));
            }
        };
        AnalysisAdmissionRepository admission = new AnalysisAdmissionRepository() {
            @Override public AnalysisAdmission lock() { return new AnalysisAdmission((short) 1, (short) 0, Instant.now()); }
        };
        ProjectAnalysisService service = new ProjectAnalysisService(projects, attempts, admission, inputs,
            Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC), 600L);

        AnalysisAttempt attempt = service.start(new AuthenticatedPrincipal(0L, "owner", Instant.MAX, ownerId), projectId, 1L);

        assertEquals(projectId, attempt.projectId());
    }
}
