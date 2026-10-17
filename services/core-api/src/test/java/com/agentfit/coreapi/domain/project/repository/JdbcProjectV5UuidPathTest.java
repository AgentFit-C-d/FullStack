package com.agentfit.coreapi.domain.project.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcProjectV5UuidPathTest {

    @Test
    void locksV5ProjectAndReadsPlanningInputUsingUuidProjectId() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        JdbcTemplate jdbc = jdbc();
        jdbc.update("INSERT INTO \"USER\" (id, name) VALUES (?, ?)", ownerId, "owner");
        jdbc.update("""
            INSERT INTO project (id, user_id, name, version, current_step, analysis_generation,
                review_generation, review_version, created_at, updated_at)
            VALUES (?, ?, 'demo', 1, 'PLANNING', 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, projectId, ownerId);

        PlanningInput input = new PlanningInput(projectId.toString(), 1L, "TEXT", null, "text/plain",
            5L, 5, null, "hello".getBytes(), "hello", null, Instant.now());
        JdbcPlanningInputRepository inputs = new JdbcPlanningInputRepository(jdbc);
        inputs.save(input);

        var locked = new JdbcProjectRepository(jdbc).findByIdForUpdate(projectId.toString()).orElseThrow();
        var loadedInput = inputs.findCurrent(projectId.toString()).orElseThrow();

        assertEquals(ownerId, locked.ownerUuid());
        assertEquals(projectId.toString(), loadedInput.projectId());
        assertArrayEquals("hello".getBytes(), loadedInput.rawContent());
    }

    private JdbcTemplate jdbc() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:project-v5-uuid-path;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE \"USER\" (id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL)");
        jdbc.execute("""
            CREATE TABLE project (
                id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES \"USER\"(id), name VARCHAR(200) NOT NULL,
                version BIGINT NOT NULL, current_step VARCHAR(30) NOT NULL, analysis_generation BIGINT NOT NULL,
                review_generation BIGINT NOT NULL, review_version BIGINT NOT NULL,
                developer_role VARCHAR(30) NOT NULL DEFAULT 'BACKEND', current_input_revision BIGINT,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)
            """);
        jdbc.execute("""
            CREATE TABLE planning_input (
                project_id UUID NOT NULL REFERENCES project(id), revision BIGINT NOT NULL, input_type VARCHAR(16) NOT NULL,
                file_name VARCHAR(255), media_type VARCHAR(100), byte_size BIGINT, character_count INTEGER,
                page_count INTEGER, raw_content BINARY VARYING, extracted_text CLOB, citations CLOB,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY (project_id, revision))
            """);
        return jdbc;
    }
}
