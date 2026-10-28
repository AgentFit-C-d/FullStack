package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisInputLease;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAnalysisInputLeaseRepository implements AnalysisInputLeaseRepository {
    private final JdbcTemplate jdbc;

    JdbcAnalysisInputLeaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AnalysisInputLease save(AnalysisInputLease lease) {
        jdbc.update(
                "INSERT INTO ANALYSIS_INPUT_LEASE "
                        + "(attempt_id, upload_id, lease_id, project_id, input_revision, expires_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                java.util.UUID.fromString(lease.attemptId()),
                lease.uploadId(),
                lease.leaseId(),
                java.util.UUID.fromString(lease.projectId()),
                lease.inputRevision(),
                Timestamp.from(lease.expiresAt()),
                Timestamp.from(lease.createdAt()));
        return lease;
    }

    @Override
    public Optional<AnalysisInputLease> findByAttemptId(String attemptId) {
        return findOne("attempt_id", attemptId);
    }

    @Override
    public Optional<AnalysisInputLease> findByUploadId(String uploadId) {
        return findOne("upload_id", uploadId);
    }

    private Optional<AnalysisInputLease> findOne(String column, Object value) {
        return jdbc.query(
                "SELECT attempt_id, upload_id, lease_id, project_id, input_revision, expires_at, created_at "
                        + "FROM ANALYSIS_INPUT_LEASE WHERE " + column + " = ?",
                (rs, row) -> new AnalysisInputLease(
                        rs.getObject("attempt_id", java.util.UUID.class).toString(),
                        rs.getString("upload_id"),
                        rs.getString("lease_id"),
                        rs.getObject("project_id", java.util.UUID.class).toString(),
                        rs.getLong("input_revision"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()),
                value)
                .stream()
                .findFirst();
    }
}
