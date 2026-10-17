package com.agentfit.coreapi.domain.analysis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAnalysisAttemptPostgresClaimIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void exactlyOneWorkerCanClaimThenTerminallyCompleteUuidAttempt() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration").load().migrate();
        Instant now = Instant.now();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        seed(userId, projectId, attemptId, now);
        JdbcAnalysisAttemptRepository repository = new JdbcAnalysisAttemptRepository(jdbc());

        AnalysisAttempt first = repository.claimNext(now, now.plusSeconds(180)).orElseThrow();

        assertEquals(attemptId.toString(), first.id());
        assertTrue(first.executionToken() != null);
        assertTrue(repository.claimNext(now, now.plusSeconds(180)).isEmpty());
        assertTrue(repository.transitionToTerminal(first.id(), first.executionToken(), "FAILED",
            "AI_ANALYSIS_FAILED", now.plusSeconds(1)));
        assertTrue(repository.findLatestByProjectId(projectId.toString()).orElseThrow().finishedAt() != null);
    }

    private void seed(UUID userId, UUID projectId, UUID attemptId, Instant now) throws Exception {
        try (Connection c = connection()) {
            try (PreparedStatement user = c.prepareStatement("INSERT INTO \"USER\" (id, name) VALUES (?, 'demo')")) {
                user.setObject(1, userId); user.executeUpdate();
            }
            try (PreparedStatement project = c.prepareStatement("""
                INSERT INTO project (id, user_id, name, version, current_step, analysis_generation, review_generation, review_version, created_at, updated_at)
                VALUES (?, ?, 'demo', 1, 'PLANNING', 1, 0, 0, ?, ?)
                """)) {
                project.setObject(1, projectId); project.setObject(2, userId);
                project.setTimestamp(3, Timestamp.from(now)); project.setTimestamp(4, Timestamp.from(now)); project.executeUpdate();
            }
            try (PreparedStatement attempt = c.prepareStatement("""
                INSERT INTO analysis_attempt (id, project_id, request_id, contract_version, generation, status, started_at, deadline_at, owner_id, input_revision, expected_project_version, expected_basis)
                VALUES (?, ?, 'request-1', '1.0', 1, 'PROCESSING', ?, ?, ?, 1, 1, 'planning-input:1')
                """)) {
                attempt.setObject(1, attemptId); attempt.setObject(2, projectId);
                attempt.setTimestamp(3, Timestamp.from(now)); attempt.setTimestamp(4, Timestamp.from(now.plusSeconds(120)));
                attempt.setObject(5, userId); attempt.executeUpdate();
            }
        }
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }
    private Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
