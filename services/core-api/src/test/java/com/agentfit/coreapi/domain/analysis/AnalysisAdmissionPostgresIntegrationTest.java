package com.agentfit.coreapi.domain.analysis;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class AnalysisAdmissionPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void rowLockAllowsAtMostTwoConcurrentAdmissions() throws Exception {
        try (Connection setup = connection()) {
            setup.createStatement().execute("CREATE TABLE analysis_admission (id SMALLINT PRIMARY KEY, active_count SMALLINT NOT NULL)");
            setup.createStatement().execute("INSERT INTO analysis_admission(id, active_count) VALUES (1, 0)");
        }
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            var results = new Future<?>[3];
            for (int i = 0; i < 3; i++) results[i] = pool.submit(this::tryAdmit);
            for (Future<?> result : results) result.get();
        } finally { pool.shutdownNow(); }
        try (Connection verify = connection(); ResultSet rs = verify.createStatement().executeQuery("SELECT active_count FROM analysis_admission WHERE id=1")) {
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
    }

    private boolean tryAdmit() {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement lock = c.prepareStatement("SELECT active_count FROM analysis_admission WHERE id=1 FOR UPDATE")) {
                try (ResultSet rs = lock.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) >= 2) { c.rollback(); return false; }
                }
            }
            try (PreparedStatement update = c.prepareStatement("UPDATE analysis_admission SET active_count = active_count + 1 WHERE id=1")) { update.executeUpdate(); }
            c.commit();
            return true;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
