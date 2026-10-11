package com.agentfit.coreapi.recommendation.persistence;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Reads B1/B3 after A has authenticated and locked the owning project in this transaction. */
public final class JdbcLatestRecommendationReader {
    public StoredRecommendationState latest(Connection transaction, String projectId) throws SQLException {
        Objects.requireNonNull(transaction);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("latest recommendation requires a caller-owned transaction");
        }
        UUID project = uuid(projectId);
        String sql = "SELECT id, project_id, project_version, confirmed_profile_id, "
            + "confirmed_profile_version, confirmation_event_id, review_version, "
            + "developer_version, environment_version, catalog_release, catalog_hash, status "
            + "FROM recommendation WHERE project_id = ? "
            + "ORDER BY created_at DESC, id DESC LIMIT 1 FOR SHARE";
        try (PreparedStatement query = transaction.prepareStatement(sql)) {
            query.setObject(1, project);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                UUID id = row.getObject("id", UUID.class);
                PreviewBasis basis = new PreviewBasis(
                    row.getObject("project_id", UUID.class).toString(), row.getLong("project_version"),
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

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("project ID is not a UUID", exception);
        }
    }
}
