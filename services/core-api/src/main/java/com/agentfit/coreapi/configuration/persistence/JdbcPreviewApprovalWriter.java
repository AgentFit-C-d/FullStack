package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

/** Fills B5's approval bundle once, inside A's authenticated project transaction. */
public final class JdbcPreviewApprovalWriter {
    public StoredApprovalState approve(Connection transaction, String actorId,
                                       StoredPreviewState preview,
                                       StoredApprovalState issued) throws SQLException {
        Objects.requireNonNull(transaction);
        Objects.requireNonNull(preview);
        Objects.requireNonNull(issued);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("approval requires a caller-owned transaction");
        }
        UUID previewId = uuid(preview.previewId());
        UUID approvalId = uuid(issued.approvalId());
        UUID actor = uuid(actorId);
        if (!preview.previewId().equals(issued.previewId())
            || !preview.fingerprint().equals(issued.fingerprint())
            || issued.approvedAt() == null || issued.expiresAt() == null
            || preview.createdAt() == null || preview.expiresAt() == null
            || issued.approvedAt().isBefore(preview.createdAt())
            || !issued.approvedAt().isBefore(issued.expiresAt())
            || issued.expiresAt().isAfter(preview.expiresAt())) {
            throw new IllegalArgumentException("approval does not match Preview metadata");
        }

        UUID recommendationId;
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT recommendation_id, fingerprint, created_at, expires_at, approval_id "
                + "FROM config_preview WHERE id = ? FOR UPDATE")) {
            query.setObject(1, previewId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("Preview no longer exists");
                if (row.getObject("approval_id") != null) {
                    throw new IllegalStateException("Preview was already approved");
                }
                if (!preview.fingerprint().equals(row.getString("fingerprint"))
                    || !preview.createdAt().equals(row.getTimestamp("created_at").toInstant())
                    || !preview.expiresAt().equals(row.getTimestamp("expires_at").toInstant())) {
                    throw new IllegalStateException("Preview metadata changed before approval");
                }
                recommendationId = row.getObject("recommendation_id", UUID.class);
            }
        }
        JdbcPreviewMetadataWriter.requireMatchingRecommendation(
            transaction, recommendationId, preview.basis());
        if (new JdbcConfigurationContextReader().byPreviewId(transaction,
            preview.basis().projectId(), preview.previewId()) == null) {
            throw new IllegalStateException("Preview context disappeared before approval");
        }

        try (PreparedStatement update = transaction.prepareStatement(
            "UPDATE config_preview SET approval_id = ?, approved_by = ?, "
                + "approved_at = ?, approval_expires_at = ? "
                + "WHERE id = ? AND approval_id IS NULL")) {
            update.setObject(1, approvalId);
            update.setObject(2, actor);
            update.setTimestamp(3, Timestamp.from(issued.approvedAt()));
            update.setTimestamp(4, Timestamp.from(issued.expiresAt()));
            update.setObject(5, previewId);
            if (update.executeUpdate() != 1) {
                throw new IllegalStateException("Preview approval was not saved");
            }
        }
        return issued;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("a database ID is not a UUID", exception);
        }
    }
}
