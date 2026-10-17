package com.agentfit.coreapi.domain.analysis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcAnalysisAttemptV5UuidTest {

    @Test
    void startsAttemptAgainstV5StatusSchema() {
        JdbcTemplate jdbc = jdbc();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO \"USER\" (id) VALUES (?)", userId);
        jdbc.update("INSERT INTO project (id) VALUES (?)", projectId);

        UUID attemptId = UUID.randomUUID();
        AnalysisAttempt started = new JdbcAnalysisAttemptRepository(jdbc).start(new AnalysisAttempt(attemptId.toString(),
            projectId.toString(), "request-1", 1L, "1.0", "PROCESSING",
            Instant.parse("2026-10-17T00:03:00Z"), Instant.parse("2026-10-17T00:00:00Z"), null,
            userId, 1L, null));

        assertEquals("PROCESSING", started.state());
        assertEquals(attemptId.toString(), started.id());
    }

    private JdbcTemplate jdbc() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:analysis-attempt-v5-uuid;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE \"USER\" (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE project (id UUID PRIMARY KEY)");
        jdbc.execute("""
            CREATE TABLE analysis_attempt (
                id UUID PRIMARY KEY, project_id UUID NOT NULL REFERENCES project(id), request_id VARCHAR(100) NOT NULL,
                contract_version VARCHAR(50) NOT NULL, generation BIGINT NOT NULL, status VARCHAR(30) NOT NULL,
                error_code VARCHAR(50), started_at TIMESTAMP WITH TIME ZONE NOT NULL,
                deadline_at TIMESTAMP WITH TIME ZONE NOT NULL, finished_at TIMESTAMP WITH TIME ZONE,
                owner_id UUID REFERENCES \"USER\"(id), input_revision BIGINT,
                expected_project_version BIGINT, expected_basis VARCHAR(100),
                UNIQUE (project_id, generation), UNIQUE (request_id))
            """);
        return jdbc;
    }
}
