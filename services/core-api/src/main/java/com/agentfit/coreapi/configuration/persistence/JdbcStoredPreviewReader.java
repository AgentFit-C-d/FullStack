package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.persistence.ProjectDbId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Reads B5 plus its immutable B1 basis in A's authenticated, project-locked transaction. */
public final class JdbcStoredPreviewReader {
    public record Snapshot(String recommendationId, StoredPreviewState preview,
                           StoredApprovalState approval) {}

    public Snapshot byPreviewId(Connection transaction, String projectId, String previewId)
        throws SQLException {
        return read(transaction, projectId, previewId, false);
    }

    public Snapshot byApprovalId(Connection transaction, String projectId, String approvalId)
        throws SQLException {
        return read(transaction, projectId, approvalId, true);
    }

    private static Snapshot read(Connection transaction, String projectId, String requestedId,
                                 boolean byApproval) throws SQLException {
        Objects.requireNonNull(transaction);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("Preview read requires a caller-owned transaction");
        }
        String project = ProjectDbId.require(projectId);
        UUID id = uuid(requestedId);
        String sql = "SELECT cp.id AS preview_id, cp.recommendation_id, cp.fingerprint, "
            + "cp.created_at, cp.expires_at, cp.approval_id, cp.approved_at, "
            + "cp.approval_expires_at, r.project_id, r.project_version, "
            + "r.confirmed_profile_id, r.confirmed_profile_version, r.confirmation_event_id, "
            + "r.review_version, r.developer_version, r.environment_version, "
            + "r.catalog_release, r.catalog_hash, r.status "
            + "FROM config_preview cp JOIN recommendation r ON r.id = cp.recommendation_id "
            + "WHERE r.project_id = ? AND "
            + (byApproval ? "cp.approval_id = ? " : "cp.id = ? ")
            + "FOR SHARE OF cp, r";
        try (PreparedStatement query = transaction.prepareStatement(sql)) {
            query.setString(1, project);
            query.setObject(2, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                if (!"RECOMMENDED".equals(row.getString("status"))) {
                    throw new IllegalStateException("Preview recommendation is not RECOMMENDED");
                }
                PreviewBasis basis = new PreviewBasis(
                    row.getString("project_id"), row.getLong("project_version"),
                    row.getObject("confirmed_profile_id", UUID.class).toString(),
                    row.getLong("confirmed_profile_version"),
                    row.getObject("confirmation_event_id", UUID.class).toString(),
                    row.getLong("review_version"), row.getLong("developer_version"),
                    row.getLong("environment_version"), row.getString("catalog_release"),
                    row.getString("catalog_hash"));
                UUID previewId = row.getObject("preview_id", UUID.class);
                String fingerprint = row.getString("fingerprint");
                StoredPreviewState preview = new StoredPreviewState(previewId.toString(), basis,
                    fingerprint, row.getTimestamp("created_at").toInstant(),
                    row.getTimestamp("expires_at").toInstant());
                UUID approvalId = row.getObject("approval_id", UUID.class);
                StoredApprovalState approval = approvalId == null ? null : new StoredApprovalState(
                    approvalId.toString(), previewId.toString(), fingerprint,
                    row.getTimestamp("approved_at").toInstant(),
                    row.getTimestamp("approval_expires_at").toInstant());
                return new Snapshot(row.getObject("recommendation_id", UUID.class).toString(),
                    preview, approval);
            }
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("a database ID is not a UUID", exception);
        }
    }
}
