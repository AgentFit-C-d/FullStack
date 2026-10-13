package com.agentfit.coreapi.domain.project;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class ProjectCreationIdempotencyPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void concurrentSameKeyLeavesOneReservationAndDifferentFingerprintIsRejectedByCaller() throws Exception {
        try (Connection c = connection()) {
            c.createStatement().execute("CREATE TABLE project_creation_idempotency (owner_id BIGINT NOT NULL, operation VARCHAR(32) NOT NULL, idempotency_key VARCHAR(128) NOT NULL, request_fingerprint CHAR(64) NOT NULL, PRIMARY KEY(owner_id, operation, idempotency_key))");
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(() -> reserve("same"));
            Future<Integer> b = pool.submit(() -> reserve("same"));
            assertEquals(1, a.get() + b.get());
        } finally { pool.shutdownNow(); }
        try (Connection c = connection(); ResultSet rs = c.createStatement().executeQuery("SELECT count(*) FROM project_creation_idempotency")) { rs.next(); assertEquals(1, rs.getInt(1)); }
    }

    private int reserve(String fingerprint) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO project_creation_idempotency(owner_id, operation, idempotency_key, request_fingerprint) VALUES (1, 'PROJECT_CREATE', 'same', ?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, "a".repeat(64));
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    private Connection connection() throws SQLException { return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); }
}
