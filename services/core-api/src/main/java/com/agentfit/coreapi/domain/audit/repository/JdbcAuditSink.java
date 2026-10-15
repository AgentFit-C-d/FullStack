package com.agentfit.coreapi.domain.audit.repository;

import com.agentfit.coreapi.domain.audit.entity.AuditEvent;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder.AuditSink;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists only the sanitized minimum audit columns; secrets and raw payloads never reach JDBC. */
@Component
public class JdbcAuditSink implements AuditSink {
    private final JdbcTemplate jdbc;

    public JdbcAuditSink(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void accept(AuditEvent event) {
        if (event.actorUuid() != null && event.projectUuid() != null) {
            jdbc.update("INSERT INTO audit_event (id, actor_id, project_id, action, result, occurred_at, reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
                java.util.UUID.randomUUID(), event.actorUuid(), event.projectUuid(), event.action(), event.result(),
                Timestamp.from(event.occurredAt()), event.reason());
            return;
        }
        jdbc.update("INSERT INTO AUDIT (actor_id, project_id, action, result, occurred_at, reason) VALUES (?, ?, ?, ?, ?, ?)",
            event.actorId(), event.projectId(), event.action(), event.result(),
            Timestamp.from(event.occurredAt()), event.reason());
    }
}
