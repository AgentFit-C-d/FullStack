package com.agentfit.coreapi.domain.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Proves that a clean PostgreSQL database accepts the canonical V5/V6 Core A UUID path. */
@Testcontainers(disabledWithoutDocker = true)
class AnalysisWorkerPostgresSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void cleanFlywaySchemaStoresUuidProjectInputAndProcessingAttempt() throws Exception {
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-17T00:00:00Z");

        try (Connection connection = connection()) {
            insertUser(connection, userId);
            insertProject(connection, projectId, userId, now);
            insertPlanningInput(connection, projectId, now);
            insertProcessingAttempt(connection, attemptId, projectId, userId, now);

            assertEquals(1, count(connection, "SELECT count(*) FROM planning_input WHERE project_id = ?", projectId));
            assertEquals(1, count(connection, "SELECT count(*) FROM analysis_attempt WHERE id = ? AND owner_id = ? AND status = 'PROCESSING'", attemptId, userId));
            assertEquals(1, count(connection, "SELECT count(*) FROM analysis_admission WHERE id = 1"));
        }
    }

    private void insertUser(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO \"USER\" (id, name) VALUES (?, ?)") ) {
            statement.setObject(1, userId);
            statement.setString(2, "demo-user");
            statement.executeUpdate();
        }
    }

    private void insertProject(Connection connection, UUID projectId, UUID userId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO project (id, user_id, name, version, current_step, analysis_generation, review_generation, review_version, created_at, updated_at)
            VALUES (?, ?, 'demo-project', 0, 'PLANNING', 0, 0, 0, ?, ?)
            """)) {
            statement.setObject(1, projectId);
            statement.setObject(2, userId);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private void insertPlanningInput(Connection connection, UUID projectId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO planning_input (project_id, revision, input_type, character_count, raw_content, extracted_text, created_at)
            VALUES (?, 1, 'TEXT', 4, ?, ?, ?)
            """)) {
            statement.setObject(1, projectId);
            statement.setBytes(2, "plan".getBytes());
            statement.setString(3, "plan");
            statement.setTimestamp(4, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private void insertProcessingAttempt(Connection connection, UUID attemptId, UUID projectId, UUID userId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO analysis_attempt (id, project_id, request_id, contract_version, generation, status, started_at, deadline_at, owner_id, input_revision)
            VALUES (?, ?, 'request-1', '1.0', 1, 'PROCESSING', ?, ?, ?, 1)
            """)) {
            statement.setObject(1, attemptId);
            statement.setObject(2, projectId);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now.plusSeconds(180)));
            statement.setObject(5, userId);
            statement.executeUpdate();
        }
    }

    private int count(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
