package com.agentfit.coreapi.recommendation.persistence;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.persistence.ProjectDbId;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Array;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Reads B1/B3 after A has authenticated and locked the owning project in this transaction. */
public final class JdbcLatestRecommendationReader {
    /** Restores B1-B4 fields needed by the latest recommendation response, without AI reruns. */
    public JdbcRecommendationMetadataWriter.Draft latestMetadata(Connection transaction,
                                                                 String projectId) throws SQLException {
        return metadata(transaction, latest(transaction, projectId));
    }

    /** Restores the exact project-owned recommendation and its B2 required Capability claims. */
    public JdbcRecommendationMetadataWriter.Draft byIdMetadata(Connection transaction,
                                                               String projectId,
                                                               String recommendationId) throws SQLException {
        return metadata(transaction, byId(transaction, projectId, recommendationId));
    }

    private static JdbcRecommendationMetadataWriter.Draft metadata(Connection transaction,
                                                                   StoredRecommendationState stored)
        throws SQLException {
        if (stored == null) return null;
        UUID id = uuid(stored.recommendationId());
        JdbcRecommendationMetadataWriter.EvidenceLevel evidence;
        Instant createdAt;
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT evidence_level, created_at FROM recommendation WHERE id = ? FOR SHARE")) {
            query.setObject(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("stored recommendation disappeared");
                evidence = JdbcRecommendationMetadataWriter.EvidenceLevel.valueOf(
                    row.getString("evidence_level"));
                createdAt = row.getTimestamp("created_at").toInstant();
            }
        }
        var draft = new JdbcRecommendationMetadataWriter.Draft(id.toString(), stored.basis(),
            stored.status(), evidence, createdAt, capabilities(transaction, id),
            items(transaction, id), notices(transaction, id));
        JdbcRecommendationMetadataWriter.validate(draft);
        return draft;
    }

    public StoredRecommendationState latest(Connection transaction, String projectId) throws SQLException {
        return read(transaction, projectId, null);
    }

    /** Reads the recommendation referenced by a Preview, scoped to A's owner-checked project. */
    public StoredRecommendationState byId(Connection transaction, String projectId,
                                          String recommendationId) throws SQLException {
        return read(transaction, projectId, uuid(recommendationId));
    }

    /** Reads B3 rows only after the same project-scoped recommendation is found. */
    public List<JdbcRecommendationMetadataWriter.Item> byIdItems(Connection transaction,
        String projectId, String recommendationId) throws SQLException {
        StoredRecommendationState stored = byId(transaction, projectId, recommendationId);
        return stored == null ? null : items(transaction, uuid(stored.recommendationId()));
    }

    private static StoredRecommendationState read(Connection transaction, String projectId,
                                                   UUID recommendationId) throws SQLException {
        Objects.requireNonNull(transaction);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("recommendation read requires a caller-owned transaction");
        }
        String project = ProjectDbId.require(projectId);
        String sql = "SELECT id, project_id, project_version, confirmed_profile_id, "
            + "confirmed_profile_version, confirmation_event_id, review_version, "
            + "developer_version, environment_version, catalog_release, catalog_hash, status "
            + "FROM recommendation WHERE project_id = ? "
            + (recommendationId == null
                ? "ORDER BY created_at DESC, id DESC LIMIT 1 FOR SHARE"
                : "AND id = ? FOR SHARE");
        try (PreparedStatement query = transaction.prepareStatement(sql)) {
            query.setString(1, project);
            if (recommendationId != null) query.setObject(2, recommendationId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                UUID id = row.getObject("id", UUID.class);
                PreviewBasis basis = new PreviewBasis(
                    row.getString("project_id"), row.getLong("project_version"),
                    row.getObject("confirmed_profile_id", UUID.class).toString(),
                    row.getLong("confirmed_profile_version"),
                    row.getObject("confirmation_event_id", UUID.class).toString(),
                    row.getLong("review_version"), row.getLong("developer_version"),
                    row.getLong("environment_version"), row.getString("catalog_release"),
                    row.getString("catalog_hash"));
                List<String> tools = toolKeys(transaction, id);
                StoredRecommendationState stored = new StoredRecommendationState(id.toString(), basis,
                    RecommendationDecision.Status.valueOf(row.getString("status")), tools);
                RecommendationReadModel.latest(stored, basis);
                return stored;
            }
        }
    }

    private static List<String> toolKeys(Connection transaction, UUID recommendationId)
        throws SQLException {
        List<String> keys = new ArrayList<>();
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT tool_key FROM recommendation_item WHERE recommendation_id = ? "
                + "ORDER BY tool_key FOR SHARE")) {
            query.setObject(1, recommendationId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) keys.add(rows.getString(1));
            }
        }
        return List.copyOf(keys);
    }

    private static List<JdbcRecommendationMetadataWriter.Capability> capabilities(
        Connection transaction, UUID id) throws SQLException {
        var result = new ArrayList<JdbcRecommendationMetadataWriter.Capability>();
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT capability_key, necessity, reason, input_refs "
                + "FROM recommendation_capability WHERE recommendation_id = ? "
                + "ORDER BY capability_key FOR SHARE")) {
            query.setObject(1, id);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(new JdbcRecommendationMetadataWriter.Capability(
                    rows.getString(1), JdbcRecommendationMetadataWriter.Necessity.valueOf(
                        rows.getString(2)), rows.getString(3), refs(rows.getArray(4))));
            }
        }
        return List.copyOf(result);
    }

    private static List<JdbcRecommendationMetadataWriter.Item> items(
        Connection transaction, UUID id) throws SQLException {
        var result = new ArrayList<JdbcRecommendationMetadataWriter.Item>();
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT id, tool_key, tool_version, support_key, required, reason, explanation_source "
                + "FROM recommendation_item WHERE recommendation_id = ? ORDER BY tool_key FOR SHARE")) {
            query.setObject(1, id);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(new JdbcRecommendationMetadataWriter.Item(
                    rows.getObject(1, UUID.class).toString(), rows.getString(2), rows.getString(3),
                    rows.getString(4), rows.getBoolean(5), rows.getString(6),
                    JdbcRecommendationMetadataWriter.ExplanationSource.valueOf(rows.getString(7))));
            }
        }
        return List.copyOf(result);
    }

    private static List<JdbcRecommendationMetadataWriter.Notice> notices(
        Connection transaction, UUID id) throws SQLException {
        var result = new ArrayList<JdbcRecommendationMetadataWriter.Notice>();
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT id, type, question_owner, answer_target, code, message, input_refs "
                + "FROM recommendation_notice WHERE recommendation_id = ? ORDER BY id FOR SHARE")) {
            query.setObject(1, id);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(new JdbcRecommendationMetadataWriter.Notice(
                    rows.getObject(1, UUID.class).toString(),
                    JdbcRecommendationMetadataWriter.NoticeType.valueOf(rows.getString(2)),
                    rows.getString(3) == null ? null
                        : JdbcRecommendationMetadataWriter.QuestionOwner.valueOf(rows.getString(3)),
                    rows.getString(4), rows.getString(5), rows.getString(6),
                    refs(rows.getArray(7))));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> refs(Array array) throws SQLException {
        try {
            return List.copyOf(Arrays.asList((String[]) array.getArray()));
        } finally {
            array.free();
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("project ID is not a UUID", exception);
        }
    }
}
