package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.persistence.ProjectDbId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Removes B1-B9 children in FK order inside A's authenticated, project-locked deletion transaction. */
public final class JdbcProjectBDataDeleter {
    private static final String PREVIEWS = "SELECT cp.id FROM config_preview cp "
        + "JOIN recommendation r ON r.id = cp.recommendation_id WHERE r.project_id = ?";
    private static final String SELECTIONS = "SELECT cs.id FROM config_selection cs "
        + "JOIN config_preview cp ON cp.id = cs.preview_id "
        + "JOIN recommendation r ON r.id = cp.recommendation_id WHERE r.project_id = ?";
    private static final String RECOMMENDATIONS = "SELECT id FROM recommendation WHERE project_id = ?";
    private static final String[] DELETE_IN_ORDER = {
        "DELETE FROM config_export WHERE preview_id IN (" + PREVIEWS + ")",
        "DELETE FROM config_preview_file WHERE preview_id IN (" + PREVIEWS + ")",
        "DELETE FROM config_permission_selection WHERE selection_id IN (" + SELECTIONS + ")",
        "DELETE FROM config_selection WHERE preview_id IN (" + PREVIEWS + ")",
        "DELETE FROM config_preview WHERE id IN (" + PREVIEWS + ")",
        "DELETE FROM recommendation_notice WHERE recommendation_id IN (" + RECOMMENDATIONS + ")",
        "DELETE FROM recommendation_capability WHERE recommendation_id IN (" + RECOMMENDATIONS + ")",
        "DELETE FROM recommendation_item WHERE recommendation_id IN (" + RECOMMENDATIONS + ")",
        "DELETE FROM recommendation WHERE project_id = ?"
    };

    public int delete(Connection transaction, String projectId) throws SQLException {
        Objects.requireNonNull(transaction);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("B data deletion requires a caller-owned transaction");
        }
        UUID project = ProjectDbId.uuid(projectId);
        try (PreparedStatement lock = transaction.prepareStatement(
            "SELECT id FROM project WHERE id = ? FOR UPDATE")) {
            lock.setObject(1, project);
            try (var row = lock.executeQuery()) {
                if (!row.next()) return 0;
            }
        }
        int deleted = 0;
        for (String sql : DELETE_IN_ORDER) {
            try (PreparedStatement statement = transaction.prepareStatement(sql)) {
                statement.setObject(1, project);
                deleted += statement.executeUpdate();
            }
        }
        return deleted;
    }
}
