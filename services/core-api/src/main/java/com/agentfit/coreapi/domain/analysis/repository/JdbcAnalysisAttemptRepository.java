package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAnalysisAttemptRepository implements AnalysisAttemptRepository {
    private final JdbcTemplate jdbc;

    JdbcAnalysisAttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AnalysisAttempt start(AnalysisAttempt attempt) {
        Long id = jdbc.queryForObject("INSERT INTO ANALYSIS_ATTEMPT (project_id, request_id, generation, contract_version, state, deadline_at, started_at) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
            Long.class, attempt.projectId(), attempt.requestId(), attempt.generation(), attempt.contractVersion(),
            attempt.state(), Timestamp.from(attempt.deadlineAt()), Timestamp.from(attempt.startedAt()));
        return new AnalysisAttempt(id == null ? 0L : id, attempt.projectId(), attempt.requestId(), attempt.generation(),
            attempt.contractVersion(), attempt.state(), attempt.deadlineAt(), attempt.startedAt(), attempt.completedAt());
    }

    @Override
    public Optional<AnalysisAttempt> findByRequestId(String requestId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT id, project_id, request_id, generation, contract_version, state, deadline_at, started_at, completed_at FROM ANALYSIS_ATTEMPT WHERE request_id = ?",
                (rs, row) -> new AnalysisAttempt(rs.getLong("id"), rs.getString("project_id"), rs.getString("request_id"),
                    rs.getLong("generation"), rs.getString("contract_version"), rs.getString("state"),
                    rs.getTimestamp("deadline_at").toInstant(), rs.getTimestamp("started_at").toInstant(),
                    rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()), requestId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void complete(String requestId, String state, Instant completedAt) {
        jdbc.update("UPDATE ANALYSIS_ATTEMPT SET state = ?, completed_at = ? WHERE request_id = ?",
            state, Timestamp.from(completedAt), requestId);
    }
}
