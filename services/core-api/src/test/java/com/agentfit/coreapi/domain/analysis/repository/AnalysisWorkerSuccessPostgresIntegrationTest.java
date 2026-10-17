package com.agentfit.coreapi.domain.analysis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAdmission;
import com.agentfit.coreapi.domain.analysis.service.AnalysisExecutionService;
import com.agentfit.coreapi.domain.analysis.service.AnalysisResultPersistenceService;
import com.agentfit.coreapi.domain.analysis.service.AnalysisWorker;
import com.agentfit.coreapi.domain.analysis.service.DefaultAnalysisWorkerTerminalCoordinator;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.project.repository.PlanningInputRepository;
import com.agentfit.coreapi.domain.project.repository.ProjectRepository;
import com.agentfit.coreapi.domain.project.service.ProfileConfirmationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AnalysisWorkerSuccessPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void workerCreatesDraftMarksSuccessAndScrubsSourceInput() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration").load().migrate();
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        seed(userId, projectId, attemptId, now);
        JdbcTemplate jdbc = jdbc();
        Project project = new Project(projectId.toString(), 0L, "demo", 1L, "PLANNING", 1L, 0L,
            now, now, "BACKEND", 1L, userId);
        StoredInput inputs = new StoredInput(jdbc, projectId, now);
        JdbcAnalysisAttemptRepository attempts = new JdbcAnalysisAttemptRepository(jdbc);
        Admission admission = new Admission();
        AnalysisResultPersistenceService persistence = new AnalysisResultPersistenceService(jdbc, new ObjectMapper(),
            Clock.fixed(now, ZoneOffset.UTC), true);
        DefaultAnalysisWorkerTerminalCoordinator terminal = new DefaultAnalysisWorkerTerminalCoordinator(
            new ProjectReader(project), attempts, admission, inputs, persistence, new AuditRecorder(),
            Clock.fixed(now, ZoneOffset.UTC));
        AiAnalysisClient client = (context, input) -> new AiAnalysisClient.AnalysisResponse(context.requestId(),
            context.generation(), context.contractVersion(), Map.of("profile", Map.of(
                "project_name", "demo", "backend", List.of("Spring Boot"))));

        new AnalysisWorker(attempts, inputs, new AnalysisExecutionService(client), terminal,
            Clock.fixed(now, ZoneOffset.UTC), 180).processOne();

        assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM analysis_attempt WHERE id=?", String.class, attemptId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM project_profile WHERE project_id=? AND kind='DRAFT'", Integer.class, projectId));
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT raw_content, extracted_text, citations FROM planning_input WHERE project_id=? AND revision=1")) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertNull(rs.getBytes(1));
                assertNull(rs.getString(2));
                assertNull(rs.getString(3));
            }
        }
        assertEquals(0, admission.count);

        new ProfileConfirmationService(jdbc, new AuditRecorder(), new ObjectMapper(),
            Clock.fixed(now, ZoneOffset.UTC), true).confirm(new AuthenticatedPrincipal(0L, "demo", null, userId),
                projectId.toString(), new ProfileConfirmationService.ConfirmRequest(1L, "confirm-1",
                    null, null, null, null, null));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM project_profile WHERE project_id=? AND kind='DRAFT'", Integer.class, projectId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM project_profile WHERE project_id=? AND kind='CONFIRMED'", Integer.class, projectId));
    }

    private void seed(UUID userId, UUID projectId, UUID attemptId, Instant now) throws Exception {
        try (Connection c = connection()) {
            execute(c, "INSERT INTO \"USER\" (id, name) VALUES (?, 'demo')", userId);
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO project (id, user_id, name, version, current_step, analysis_generation, review_generation, review_version, created_at, updated_at)
                VALUES (?, ?, 'demo', 1, 'PLANNING', 1, 0, 0, ?, ?)
                """)) {
                ps.setObject(1, projectId); ps.setObject(2, userId); ps.setTimestamp(3, Timestamp.from(now)); ps.setTimestamp(4, Timestamp.from(now)); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO planning_input (project_id, revision, input_type, character_count, raw_content, extracted_text, citations, created_at)
                VALUES (?, 1, 'TEXT', 4, ?, 'plan', 'citation', ?)
                """)) {
                ps.setObject(1, projectId); ps.setBytes(2, "plan".getBytes(StandardCharsets.UTF_8)); ps.setTimestamp(3, Timestamp.from(now)); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO analysis_attempt (id, project_id, request_id, contract_version, generation, status, started_at, deadline_at, owner_id, input_revision, expected_project_version, expected_basis)
                VALUES (?, ?, 'request-1', '1.0', 1, 'PROCESSING', ?, ?, ?, 1, 1, 'planning-input:1')
                """)) {
                ps.setObject(1, attemptId); ps.setObject(2, projectId); ps.setTimestamp(3, Timestamp.from(now)); ps.setTimestamp(4, Timestamp.from(now.plusSeconds(120))); ps.setObject(5, userId); ps.executeUpdate();
            }
        }
    }

    private void execute(Connection c, String sql, UUID id) throws Exception { try (PreparedStatement ps = c.prepareStatement(sql)) { ps.setObject(1, id); ps.executeUpdate(); } }
    private JdbcTemplate jdbc() { return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())); }
    private Connection connection() throws Exception { return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); }

    private static final class ProjectReader implements ProjectRepository {
        private final Project project;
        ProjectReader(Project project) { this.project = project; }
        @Override public Project save(Project value) { return value; }
        @Override public List<Project> findByOwnerId(long ownerId) { return List.of(); }
        @Override public Optional<Project> findByIdAndOwnerId(String id, long ownerId) { return Optional.empty(); }
        @Override public Optional<Project> findByIdForUpdate(String id) { return project.id().equals(id) ? Optional.of(project) : Optional.empty(); }
    }

    private static final class StoredInput implements PlanningInputRepository {
        private final JdbcTemplate jdbc; private final UUID projectId; private final Instant createdAt;
        StoredInput(JdbcTemplate jdbc, UUID projectId, Instant createdAt) { this.jdbc = jdbc; this.projectId = projectId; this.createdAt = createdAt; }
        @Override public Optional<PlanningInput> findByProjectIdAndRevision(String id, long revision) {
            return Optional.of(new PlanningInput(id, revision, "TEXT", null, "text/plain", 4L, 4, null,
                "plan".getBytes(StandardCharsets.UTF_8), "plan", "citation", createdAt));
        }
        @Override public void scrub(String id, long revision) { jdbc.update("UPDATE planning_input SET raw_content=NULL, extracted_text=NULL, citations=NULL WHERE project_id=? AND revision=?", projectId, revision); }
    }

    private static final class Admission implements com.agentfit.coreapi.domain.analysis.repository.AnalysisAdmissionRepository {
        private int count = 1;
        @Override public AnalysisAdmission lock() { return new AnalysisAdmission((short) 1, (short) count, Instant.now()); }
        @Override public void decrement() { count--; }
    }
}
