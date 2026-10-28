package com.agentfit.coreapi.recommendation.answers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.global.exception.BusinessException;
import com.agentfit.coreapi.global.exception.ErrorCode;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** Stores user answers as annotations; recommendation and confirmed facts remain unchanged. */
@Service
public class RecommendationQuestionAnswerService {
    private final JdbcTemplate jdbc;
    private final Supplier<RecommendationLatestDetailsService> latest;

    public RecommendationQuestionAnswerService(JdbcTemplate jdbc,
                                               RecommendationLatestDetailsService latest) {
        this.jdbc = jdbc;
        this.latest = () -> latest;
    }

    @Autowired
    public RecommendationQuestionAnswerService(JdbcTemplate jdbc,
            ObjectProvider<RecommendationLatestDetailsService> latest) {
        this.jdbc = jdbc;
        this.latest = latest::getIfAvailable;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public RecommendationQuestionAnswer save(String projectId, String recommendationId,
                                              String noticeId, String answer, UUID actorId) {
        UUID project = UUID.fromString(projectId);
        UUID recommendation = UUID.fromString(recommendationId);
        UUID notice = UUID.fromString(noticeId);
        if (actorId == null) throw new IllegalArgumentException("authenticated UUID actor required");
        String value = RecommendationQuestionAnswer.validatedAnswer(answer);
        var current = latest.get();
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "recommendation reader unavailable");
        }
        var snapshot = current.latest(projectId);
        if (snapshot == null || snapshot.summary() == null
            || !recommendation.toString().equals(snapshot.summary().recommendationId())
            || snapshot.summary().validity() != RecommendationReadModel.Validity.CURRENT
            || snapshot.summary().status() != RecommendationDecision.Status.NEEDS_INFORMATION) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT,
                "answer requires the current information request");
        }
        List<String> targets = jdbc.query("""
            SELECT n.answer_target FROM recommendation_notice n
            JOIN recommendation r ON r.id = n.recommendation_id
            WHERE r.project_id = ? AND r.id = ? AND r.status = 'NEEDS_INFORMATION'
              AND n.id = ? AND n.type = 'QUESTION'
            FOR UPDATE OF n
            """, (rs, row) -> rs.getString(1), project, recommendation, notice);
        if (targets.isEmpty()) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "question not found");
        jdbc.update("""
            INSERT INTO recommendation_question_answer
                (notice_id, recommendation_id, project_id, answer, actor_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (notice_id) DO UPDATE SET
                answer = EXCLUDED.answer, actor_id = EXCLUDED.actor_id,
                updated_at = CURRENT_TIMESTAMP
            """, notice, recommendation, project, value, actorId);
        return answerByNotice(recommendation, notice);
    }

    @Transactional(readOnly = true)
    public List<RecommendationQuestionAnswer> list(String projectId, String recommendationId) {
        UUID project = UUID.fromString(projectId);
        UUID recommendation = UUID.fromString(recommendationId);
        List<UUID> known = jdbc.query("SELECT id FROM recommendation WHERE id = ? AND project_id = ?",
            (rs, row) -> rs.getObject(1, UUID.class), recommendation, project);
        if (known.isEmpty()) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "recommendation not found");
        return jdbc.query("""
            SELECT a.notice_id, a.recommendation_id, n.answer_target, a.answer,
                   a.actor_id, a.created_at, a.updated_at
            FROM recommendation_question_answer a
            JOIN recommendation_notice n ON n.id = a.notice_id
            WHERE a.project_id = ? AND a.recommendation_id = ?
            ORDER BY n.code, a.notice_id
            """, RecommendationQuestionAnswerService::map, project, recommendation);
    }

    private RecommendationQuestionAnswer answerByNotice(UUID recommendation, UUID notice) {
        return jdbc.queryForObject("""
            SELECT a.notice_id, a.recommendation_id, n.answer_target, a.answer,
                   a.actor_id, a.created_at, a.updated_at
            FROM recommendation_question_answer a
            JOIN recommendation_notice n ON n.id = a.notice_id
            WHERE a.recommendation_id = ? AND a.notice_id = ?
            """, RecommendationQuestionAnswerService::map, recommendation, notice);
    }

    private static RecommendationQuestionAnswer map(ResultSet rs, int row) throws SQLException {
        return new RecommendationQuestionAnswer(rs.getObject(1, UUID.class),
            rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
            rs.getObject(5, UUID.class), rs.getTimestamp(6).toInstant(),
            rs.getTimestamp(7).toInstant());
    }
}
