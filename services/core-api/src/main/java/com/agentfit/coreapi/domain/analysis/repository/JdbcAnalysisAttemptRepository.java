package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAnalysisAttemptRepository implements AnalysisAttemptRepository {
    private static final String COLUMNS = "id, project_id, request_id, generation, contract_version, status, deadline_at, started_at, finished_at, owner_id, input_revision, error_code, execution_token, claimed_at, lease_expires_at, expected_project_version, expected_basis";
    private static final String RETURNING_COLUMNS = "attempt.id, attempt.project_id, attempt.request_id, attempt.generation, attempt.contract_version, attempt.status, attempt.deadline_at, attempt.started_at, attempt.finished_at, attempt.owner_id, attempt.input_revision, attempt.error_code, attempt.execution_token, attempt.claimed_at, attempt.lease_expires_at, attempt.expected_project_version, attempt.expected_basis";
    private final JdbcTemplate jdbc;

    JdbcAnalysisAttemptRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public AnalysisAttempt start(AnalysisAttempt attempt) {
        jdbc.update("INSERT INTO analysis_attempt (id, project_id, request_id, generation, contract_version, status, deadline_at, started_at, owner_id, input_revision, expected_project_version, expected_basis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.fromString(attempt.id()), UUID.fromString(attempt.projectId()), attempt.requestId(), attempt.generation(),
            attempt.contractVersion(), attempt.status(), Timestamp.from(attempt.deadlineAt()),
            Timestamp.from(attempt.startedAt()), attempt.ownerUuid(), attempt.inputRevision(), attempt.expectedProjectVersion(), attempt.expectedBasis());
        return attempt;
    }

    @Override
    public Optional<AnalysisAttempt> findByRequestId(String requestId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT " + COLUMNS + " FROM analysis_attempt WHERE request_id = ?", (rs, row) -> map(rs), requestId));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    @Override
    public void complete(String requestId, String status, Instant finishedAt) {
        jdbc.update("UPDATE analysis_attempt SET status = ?, finished_at = ? WHERE request_id = ? AND status = 'PROCESSING'", status, Timestamp.from(finishedAt), requestId);
    }

    @Override public Optional<AnalysisAttempt> findProcessingByProjectId(String projectId) { return findOne("project_id", UUID.fromString(projectId)); }
    @Override public Optional<AnalysisAttempt> findProcessingByOwnerUuid(UUID ownerId) { return findOne("owner_id", ownerId); }
    @Override public Optional<AnalysisAttempt> findLatestByProjectId(String projectId) { return jdbc.query("SELECT " + COLUMNS + " FROM analysis_attempt WHERE project_id=? ORDER BY generation DESC LIMIT 1", (rs, n) -> map(rs), UUID.fromString(projectId)).stream().findFirst(); }

    private Optional<AnalysisAttempt> findOne(String column, Object value) {
        return jdbc.query("SELECT " + COLUMNS + " FROM analysis_attempt WHERE " + column + "=? AND status='PROCESSING' LIMIT 1", (rs, n) -> map(rs), value).stream().findFirst();
    }

    @Override public List<AnalysisAttempt> findExpired(Instant now) { return jdbc.query("SELECT " + COLUMNS + " FROM analysis_attempt WHERE status='PROCESSING' AND deadline_at<=?", (rs, n) -> map(rs), Timestamp.from(now)); }
    @Override public Optional<AnalysisAttempt> claimNext(Instant now, Instant leaseExpiresAt) {
        UUID token = UUID.randomUUID();
        String sql = """
            WITH candidate AS (
              SELECT id FROM analysis_attempt
               WHERE status = 'PROCESSING' AND execution_token IS NULL AND deadline_at > ?
               ORDER BY started_at ASC
               LIMIT 1 FOR UPDATE SKIP LOCKED
            )
            UPDATE analysis_attempt attempt
               SET execution_token = ?, claimed_at = ?, lease_expires_at = ?
              FROM candidate
             WHERE attempt.id = candidate.id
            RETURNING %s
            """.formatted(RETURNING_COLUMNS);
        return jdbc.query(sql, (rs, n) -> map(rs), Timestamp.from(now), token, Timestamp.from(now), Timestamp.from(leaseExpiresAt)).stream().findFirst();
    }
    @Override public boolean transitionToSucceeded(String id, UUID token, String draftId, long draftVersion, Instant finishedAt) {
        return jdbc.update("UPDATE analysis_attempt SET status='SUCCEEDED', result_draft_id=?, result_draft_version=?, finished_at=? WHERE id=? AND status='PROCESSING' AND execution_token=?", UUID.fromString(draftId), draftVersion, Timestamp.from(finishedAt), UUID.fromString(id), token) == 1;
    }
    @Override public boolean transitionToTerminal(String id, UUID token, String status, String errorCode, Instant finishedAt) {
        return jdbc.update("UPDATE analysis_attempt SET status=?, error_code=?, finished_at=? WHERE id=? AND status='PROCESSING' AND execution_token=?", status, errorCode, Timestamp.from(finishedAt), UUID.fromString(id), token) == 1;
    }
    @Override public boolean transitionToTerminal(String id, String status, String errorCode, Instant finishedAt) { return jdbc.update("UPDATE analysis_attempt SET status=?, error_code=?, finished_at=? WHERE id=? AND status='PROCESSING'", status, errorCode, Timestamp.from(finishedAt), UUID.fromString(id)) == 1; }

    private AnalysisAttempt map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AnalysisAttempt(rs.getObject("id", UUID.class).toString(), rs.getObject("project_id", UUID.class).toString(), rs.getString("request_id"), rs.getLong("generation"), rs.getString("contract_version"), rs.getString("status"), rs.getTimestamp("deadline_at").toInstant(), rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(), rs.getObject("owner_id", UUID.class), (Long) rs.getObject("input_revision"), rs.getString("error_code"), rs.getObject("execution_token", UUID.class), rs.getTimestamp("claimed_at") == null ? null : rs.getTimestamp("claimed_at").toInstant(), rs.getTimestamp("lease_expires_at") == null ? null : rs.getTimestamp("lease_expires_at").toInstant(), (Long) rs.getObject("expected_project_version"), rs.getString("expected_basis"));
    }
}
