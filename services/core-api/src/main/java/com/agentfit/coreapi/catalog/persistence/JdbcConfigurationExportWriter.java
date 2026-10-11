package com.agentfit.coreapi.catalog.persistence;

import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Writes only B9 generation metadata in A's authenticated, project-locked transaction. */
public final class JdbcConfigurationExportWriter {
    public void save(Connection transaction, ConfigurationGenerationHistory history) throws SQLException {
        Objects.requireNonNull(transaction);
        Objects.requireNonNull(history);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("export requires a caller-owned transaction");
        }
        UUID id = uuid(history.id());
        UUID previewId = uuid(history.previewId());
        UUID recommendationId = uuid(history.recommendationId());
        UUID approvalId = uuid(history.approvalId());
        if (history.basis() == null || history.createdAt() == null
            || history.fingerprint() == null || !history.fingerprint().matches("[0-9a-f]{64}")
            || history.files() == null || history.files().isEmpty()) {
            throw new IllegalArgumentException("incomplete export metadata");
        }

        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT recommendation_id, fingerprint, approval_id, approved_at, "
                + "approval_expires_at, created_at, expires_at "
                + "FROM config_preview WHERE id = ? FOR UPDATE")) {
            query.setObject(1, previewId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next() || !recommendationId.equals(row.getObject("recommendation_id", UUID.class))
                    || !approvalId.equals(row.getObject("approval_id", UUID.class))
                    || !history.fingerprint().equals(row.getString("fingerprint"))
                    || !within(history.createdAt(), row.getTimestamp("created_at"),
                        row.getTimestamp("expires_at"))
                    || !within(history.createdAt(), row.getTimestamp("approved_at"),
                        row.getTimestamp("approval_expires_at"))) {
                    throw new IllegalStateException("export does not match an active approved Preview");
                }
            }
        }
        JdbcPreviewMetadataWriter.requireMatchingRecommendation(transaction, recommendationId,
            history.basis());
        requireMatchingFiles(transaction, previewId, history);

        try (PreparedStatement insert = transaction.prepareStatement(
            "INSERT INTO config_export (id, preview_id, fingerprint, generated_at) VALUES (?, ?, ?, ?)")) {
            insert.setObject(1, id);
            insert.setObject(2, previewId);
            insert.setString(3, history.fingerprint());
            insert.setTimestamp(4, Timestamp.from(history.createdAt()));
            insert.executeUpdate();
        }
    }

    private static void requireMatchingFiles(Connection connection, UUID previewId,
                                             ConfigurationGenerationHistory history) throws SQLException {
        Set<FileKey> generated = new HashSet<>();
        for (var file : history.files()) {
            if (file == null || file.relativePath() == null || file.action() == null
                || file.afterHash() == null || !file.afterHash().matches("[0-9a-f]{64}")
                || !generated.add(new FileKey(file.relativePath(), file.action().name(), file.afterHash()))) {
                throw new IllegalArgumentException("invalid generated file metadata");
            }
        }
        Set<FileKey> stored = new HashSet<>();
        int count = 0;
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT relative_path, action, after_hash FROM config_preview_file WHERE preview_id = ?")) {
            query.setObject(1, previewId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    count++;
                    stored.add(new FileKey(rows.getString(1), rows.getString(2), rows.getString(3)));
                }
            }
        }
        if (count != generated.size() || !stored.equals(generated)) {
            throw new IllegalStateException("generated files differ from approved Preview metadata");
        }
    }

    private static boolean within(Instant time, Timestamp from, Timestamp until) {
        return from != null && until != null && !time.isBefore(from.toInstant())
            && time.isBefore(until.toInstant());
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("a database ID is not a UUID", exception);
        }
    }

    private record FileKey(String path, String action, String hash) {}
}
