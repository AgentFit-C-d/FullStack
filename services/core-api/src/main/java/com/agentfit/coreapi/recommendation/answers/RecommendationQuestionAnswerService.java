package com.agentfit.coreapi.recommendation.answers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.agentfit.coreapi.integration.ab.RecommendationInputsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Applies a current environment question answer and records the user's answer atomically. */
@Service
public class RecommendationQuestionAnswerService {
    private final JdbcTemplate jdbc;
    private final Supplier<RecommendationLatestDetailsService> latest;
    private final Supplier<RecommendationInputsService> inputs;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecommendationQuestionAnswerService(JdbcTemplate jdbc,
                                               RecommendationLatestDetailsService latest) {
        this.jdbc = jdbc;
        this.latest = () -> latest;
        this.inputs = () -> null;
    }

    @Autowired
    public RecommendationQuestionAnswerService(JdbcTemplate jdbc,
            ObjectProvider<RecommendationLatestDetailsService> latest,
            ObjectProvider<RecommendationInputsService> inputs) {
        this.jdbc = jdbc;
        this.latest = latest::getIfAvailable;
        this.inputs = inputs::getIfAvailable;
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
        if (jdbc.queryForList("SELECT id FROM project WHERE id = ? FOR UPDATE", UUID.class, project).isEmpty()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "project not found");
        }
        var locked = current.latest(projectId);
        if (locked == null || locked.summary() == null
            || !recommendation.toString().equals(locked.summary().recommendationId())
            || locked.summary().validity() != RecommendationReadModel.Validity.CURRENT) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "recommendation changed before answer save");
        }
        List<String> targets = jdbc.query("""
            SELECT n.answer_target FROM recommendation_notice n
            JOIN recommendation r ON r.id = n.recommendation_id
            WHERE r.project_id = ? AND r.id = ? AND r.status = 'NEEDS_INFORMATION'
              AND n.id = ? AND n.type = 'QUESTION'
            FOR UPDATE OF n
            """, (rs, row) -> rs.getString(1), project, recommendation, notice);
        if (targets.isEmpty()) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "question not found");
        applyEnvironmentAnswer(projectId, targets.get(0), value);
        jdbc.update("""
            INSERT INTO recommendation_question_answer
                (notice_id, recommendation_id, project_id, answer, actor_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (notice_id) DO UPDATE SET
                answer = EXCLUDED.answer, actor_id = EXCLUDED.actor_id,
                updated_at = CURRENT_TIMESTAMP
            """, notice, recommendation, project, value, actorId);
        jdbc.update("""
            INSERT INTO audit_event (id, project_id, actor_id, event, outcome, target_type, target_id, occurred_at)
            VALUES (?, ?, ?, 'RECOMMENDATION_QUESTION_ANSWERED', 'SUCCEEDED', 'RECOMMENDATION_NOTICE', ?, CURRENT_TIMESTAMP)
            """, UUID.randomUUID(), project, actorId, notice);
        return answerByNotice(recommendation, notice);
    }

    private void applyEnvironmentAnswer(String projectId, String target, String answer) {
        if (!target.startsWith("environment.")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "this question requires the related planning input to be updated");
        }
        RecommendationInputsService service = inputs.get();
        if (service == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "environment input writer unavailable");
        JsonNode value;
        try { value = mapper.readTree(answer); }
        catch (Exception ex) { throw new IllegalArgumentException("answer must be structured JSON", ex); }
        if (value == null) throw new IllegalArgumentException("answer must be structured JSON");
        var current = service.current(projectId).environment();
        String os = current == null ? null : current.osFamily();
        String client = current == null ? null : current.clientId();
        String version = current == null ? null : current.clientVersion();
        boolean known = current != null && current.installedComponentsKnown();
        List<RecommendationInputsService.InstalledComponent> components = current == null
            ? List.of() : current.installedComponents();
        switch (target) {
            case "environment.target" -> {
                if (!value.isObject() || value.size() != 3
                    || !value.path("osFamily").isTextual() || !value.path("clientId").isTextual()
                    || !value.path("clientVersion").isTextual()) {
                    throw new IllegalArgumentException("target answer requires osFamily, clientId, clientVersion");
                }
                os = value.path("osFamily").asText();
                client = value.path("clientId").asText();
                version = value.path("clientVersion").asText();
            }
            case "environment.installedComponents" -> {
                if (!value.isArray() || value.size() > 30) {
                    throw new IllegalArgumentException("installed components answer must be an array");
                }
                var parsed = new java.util.ArrayList<RecommendationInputsService.InstalledComponent>();
                for (JsonNode item : value) {
                    if (!item.isObject() || !item.path("toolKey").isTextual()
                        || item.has("version") && !item.get("version").isNull()
                            && !item.get("version").isTextual()) {
                        throw new IllegalArgumentException("installed component requires toolKey and optional version");
                    }
                    parsed.add(new RecommendationInputsService.InstalledComponent(
                        item.path("toolKey").asText(), item.path("version").isTextual()
                            ? item.path("version").asText() : null));
                }
                components = List.copyOf(parsed);
                known = true;
            }
            case "environment.installedComponentVersions" -> {
                if (!value.isObject() || current == null || !known) {
                    throw new IllegalArgumentException("component versions require a known installed list");
                }
                var parsed = new java.util.ArrayList<RecommendationInputsService.InstalledComponent>();
                for (var item : components) {
                    JsonNode given = value.get(item.toolKey());
                    if (given != null && !given.isTextual()) throw new IllegalArgumentException("version must be text");
                    parsed.add(new RecommendationInputsService.InstalledComponent(item.toolKey(),
                        given == null ? item.version() : given.asText()));
                }
                if (value.size() > parsed.size() || parsed.stream().filter(item -> value.has(item.toolKey())).count() != value.size()) {
                    throw new IllegalArgumentException("unknown installed component");
                }
                components = List.copyOf(parsed);
            }
            default -> throw new IllegalArgumentException("unsupported answer target");
        }
        if (os == null || client == null) {
            throw new IllegalArgumentException("OS and client must be saved before this answer");
        }
        service.saveEnvironment(projectId, current == null ? 0 : current.version(),
            os, client, version, known, components);
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
