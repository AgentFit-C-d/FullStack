package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAnalysisResultRepository implements AnalysisResultRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcAnalysisResultRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisResult save(AnalysisResult result) {
        try {
            jdbc.update(
                    "INSERT INTO ANALYSIS_RESULT "
                            + "(attempt_id, request_id, generation, contract_version, result_json, created_at) "
                            + "VALUES (?, ?, ?, ?, CAST(? AS JSONB), ?)",
                    result.attemptId(),
                    result.requestId(),
                    result.generation(),
                    result.contractVersion(),
                    objectMapper.writeValueAsString(result.result()),
                    Timestamp.from(result.createdAt()));
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize analysis result", exception);
        }
    }

    @Override
    public Optional<AnalysisResult> findByAttemptId(long attemptId) {
        return jdbc.query(
                "SELECT attempt_id, request_id, generation, contract_version, result_json, created_at "
                        + "FROM ANALYSIS_RESULT WHERE attempt_id = ?",
                (rs, row) -> new AnalysisResult(
                        rs.getLong("attempt_id"),
                        rs.getString("request_id"),
                        rs.getLong("generation"),
                        rs.getString("contract_version"),
                        readResult(rs.getString("result_json")),
                        rs.getTimestamp("created_at").toInstant()),
                attemptId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AnalysisResult> findLatestByProjectId(String projectId) {
        return jdbc.query(
                "SELECT r.attempt_id, r.request_id, r.generation, r.contract_version, r.result_json, r.created_at "
                        + "FROM ANALYSIS_RESULT r JOIN ANALYSIS_ATTEMPT a ON a.id = r.attempt_id "
                        + "WHERE a.project_id = ? ORDER BY r.generation DESC LIMIT 1",
                (rs, row) -> new AnalysisResult(
                        rs.getLong("attempt_id"),
                        rs.getString("request_id"),
                        rs.getLong("generation"),
                        rs.getString("contract_version"),
                        readResult(rs.getString("result_json")),
                        rs.getTimestamp("created_at").toInstant()),
                projectId)
                .stream()
                .findFirst();
    }

    @Override
    public void updateHumanReview(long attemptId, Map<String, Object> review) {
        try {
            jdbc.update(
                    "UPDATE ANALYSIS_RESULT SET result_json = result_json || CAST(? AS JSONB) WHERE attempt_id = ?",
                    objectMapper.writeValueAsString(Map.of("humanReview", review)),
                    attemptId);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize human review", exception);
        }
    }

    private Map<String, Object> readResult(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize analysis result", exception);
        }
    }
}
