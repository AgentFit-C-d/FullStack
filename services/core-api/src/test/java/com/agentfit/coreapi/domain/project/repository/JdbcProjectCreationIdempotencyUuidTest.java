package com.agentfit.coreapi.domain.project.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.project.entity.ProjectCreationIdempotency;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcProjectCreationIdempotencyUuidTest {

    @Test
    void storesAndFindsReservationWithV5UuidOwner() {
        JdbcTemplate jdbc = jdbc();
        UUID ownerId = UUID.randomUUID();
        JdbcProjectCreationIdempotencyRepository repository = new JdbcProjectCreationIdempotencyRepository(jdbc);
        ProjectCreationIdempotency reservation = new ProjectCreationIdempotency(ownerId, "PROJECT_CREATE",
            "create-1", "a".repeat(64), null, Instant.parse("2026-10-17T00:00:00Z"));

        repository.reserveByOwnerUuid(reservation);
        var loaded = repository.findByOwnerUuid(ownerId, "PROJECT_CREATE", "create-1").orElseThrow();

        assertEquals(ownerId, loaded.ownerUuid());
        assertEquals("create-1", loaded.idempotencyKey());
    }

    private JdbcTemplate jdbc() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:project-idempotency-uuid;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE project_creation_idempotency (owner_id UUID NOT NULL, operation VARCHAR(32) NOT NULL, "
            + "idempotency_key VARCHAR(128) NOT NULL, request_fingerprint CHAR(64) NOT NULL, project_id UUID, "
            + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY(owner_id, operation, idempotency_key))");
        return jdbc;
    }
}
