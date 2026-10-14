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
        Long id = jdbc.queryForObject("INSERT INTO ANALYSIS_ATTEMPT (project_id, request_id, generation, contract_version, state, deadline_at, started_at, owner_id, input_revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
            Long.class, attempt.projectId(), attempt.requestId(), attempt.generation(), attempt.contractVersion(),
            attempt.state(), Timestamp.from(attempt.deadlineAt()), Timestamp.from(attempt.startedAt()), attempt.ownerId(), attempt.inputRevision());
        return new AnalysisAttempt(id == null ? 0L : id, attempt.projectId(), attempt.requestId(), attempt.generation(),
            attempt.contractVersion(), attempt.state(), attempt.deadlineAt(), attempt.startedAt(), attempt.completedAt());
    }

    @Override
    public Optional<AnalysisAttempt> findByRequestId(String requestId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT id, project_id, request_id, generation, contract_version, state, deadline_at, started_at, completed_at, owner_id, input_revision, error_code FROM ANALYSIS_ATTEMPT WHERE request_id = ?",
                (rs, row) -> new AnalysisAttempt(rs.getLong("id"), rs.getString("project_id"), rs.getString("request_id"),
                    rs.getLong("generation"), rs.getString("contract_version"), rs.getString("state"),
                    rs.getTimestamp("deadline_at").toInstant(), rs.getTimestamp("started_at").toInstant(),
                    rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(), (Long)rs.getObject("owner_id"), (Long)rs.getObject("input_revision"), rs.getString("error_code")), requestId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void complete(String requestId, String state, Instant completedAt) {
        jdbc.update("UPDATE ANALYSIS_ATTEMPT SET state = ?, completed_at = ? WHERE request_id = ?",
            state, Timestamp.from(completedAt), requestId);
    }

    public Optional<AnalysisAttempt> findProcessingByProjectId(String projectId) { return findOne("project_id", projectId); }
    public Optional<AnalysisAttempt> findProcessingByOwnerId(long ownerId) { return findOne("owner_id", ownerId); }
    public Optional<AnalysisAttempt> findLatestByProjectId(String projectId) { return jdbc.query("SELECT id,project_id,request_id,generation,contract_version,state,deadline_at,started_at,completed_at,owner_id,input_revision,error_code FROM ANALYSIS_ATTEMPT WHERE project_id=? ORDER BY generation DESC LIMIT 1", (rs,n)->new AnalysisAttempt(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getTimestamp(7).toInstant(),rs.getTimestamp(8).toInstant(),rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant(),(Long)rs.getObject(10),(Long)rs.getObject(11),rs.getString(12)), projectId).stream().findFirst(); }
    private Optional<AnalysisAttempt> findOne(String column,Object value) { return jdbc.query("SELECT id,project_id,request_id,generation,contract_version,state,deadline_at,started_at,completed_at,owner_id,input_revision,error_code FROM ANALYSIS_ATTEMPT WHERE "+column+"=? AND state='PROCESSING' LIMIT 1", (rs,n)->new AnalysisAttempt(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getTimestamp(7).toInstant(),rs.getTimestamp(8).toInstant(),rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant(),(Long)rs.getObject(10),(Long)rs.getObject(11),rs.getString(12)), value).stream().findFirst(); }
    public java.util.List<AnalysisAttempt> findExpired(Instant now) { return jdbc.query("SELECT id,project_id,request_id,generation,contract_version,state,deadline_at,started_at,completed_at,owner_id,input_revision,error_code FROM ANALYSIS_ATTEMPT WHERE state='PROCESSING' AND deadline_at<=?", (rs,n)->new AnalysisAttempt(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getTimestamp(7).toInstant(),rs.getTimestamp(8).toInstant(),null,(Long)rs.getObject(10),(Long)rs.getObject(11),rs.getString(12)), Timestamp.from(now)); }
    public boolean transitionToTerminal(long id,String state,String errorCode,Instant completedAt){ return jdbc.update("UPDATE ANALYSIS_ATTEMPT SET state=?,error_code=?,completed_at=? WHERE id=? AND state='PROCESSING'",state,errorCode,Timestamp.from(completedAt),id)==1; }
}
