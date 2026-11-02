package com.agentfit.coreapi.domain.audit.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.audit.entity.AuditEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcAuditSinkUuidTest {
    @Test
    void recordsUuidProjectEventWithLegacyActorIdInCanonicalAuditTable() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:audit-uuid;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"USER\" (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS project (id UUID PRIMARY KEY, user_id UUID NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS audit_event (id UUID PRIMARY KEY, actor_id UUID, project_id UUID, event VARCHAR(100), outcome VARCHAR(30), occurred_at TIMESTAMP WITH TIME ZONE, reason_code VARCHAR(100))");
        UUID actorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO \"USER\" (id) VALUES (?)", actorId);
        jdbc.update("INSERT INTO project (id, user_id) VALUES (?, ?)", projectId, actorId);

        new JdbcAuditSink(jdbc).accept(new AuditEvent(1L, projectId.toString(),
            "ANALYSIS_STARTED", "SUCCEEDED", Instant.now(), null, Map.of()));

        assertEquals(actorId, jdbc.queryForObject(
            "SELECT actor_id FROM audit_event WHERE project_id = ?", UUID.class, projectId));
    }
}
