package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Reads content-free B9/B8 history and latest USER report in A's owner-checked transaction. */
public final class JdbcConfigurationHistoryReader {
    public record Snapshot(ConfigurationGenerationHistory generation,
                           ConfigurationHistoryReadModel.UserReport latestUserReport) {}

    public Snapshot find(Connection transaction, String projectId, String generationId)
        throws SQLException {
        Objects.requireNonNull(transaction);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("history read requires a caller-owned transaction");
        }
        UUID project = uuid(projectId);
        UUID export = uuid(generationId);
        String sql = "SELECT e.id AS export_id, e.fingerprint AS export_fingerprint, e.generated_at, "
            + "e.latest_report_id, e.reported_state, e.reason_code, e.reported_at, "
            + "cp.id AS preview_id, cp.fingerprint AS preview_fingerprint, cp.approval_id, "
            + "cp.approved_at, cp.approval_expires_at, cp.recommendation_id, "
            + "r.project_id, r.project_version, r.confirmed_profile_id, "
            + "r.confirmed_profile_version, r.confirmation_event_id, r.review_version, "
            + "r.developer_version, r.environment_version, r.catalog_release, r.catalog_hash "
            + "FROM config_export e JOIN config_preview cp ON cp.id = e.preview_id "
            + "JOIN recommendation r ON r.id = cp.recommendation_id "
            + "WHERE e.id = ? AND r.project_id = ? FOR SHARE OF e, cp, r";
        try (PreparedStatement query = transaction.prepareStatement(sql)) {
            query.setObject(1, export);
            query.setObject(2, project);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                UUID approvalId = row.getObject("approval_id", UUID.class);
                Instant generatedAt = row.getTimestamp("generated_at").toInstant();
                if (approvalId == null
                    || !row.getString("export_fingerprint").equals(row.getString("preview_fingerprint"))
                    || generatedAt.isBefore(row.getTimestamp("approved_at").toInstant())
                    || !generatedAt.isBefore(row.getTimestamp("approval_expires_at").toInstant())) {
                    throw new IllegalStateException("stored export no longer matches approved Preview");
                }
                UUID previewId = row.getObject("preview_id", UUID.class);
                PreviewBasis basis = new PreviewBasis(
                    row.getObject("project_id", UUID.class).toString(), row.getLong("project_version"),
                    row.getObject("confirmed_profile_id", UUID.class).toString(),
                    row.getLong("confirmed_profile_version"),
                    row.getObject("confirmation_event_id", UUID.class).toString(),
                    row.getLong("review_version"), row.getLong("developer_version"),
                    row.getLong("environment_version"), row.getString("catalog_release"),
                    row.getString("catalog_hash"));
                var history = ConfigurationGenerationHistory.fromStoredMetadata(
                    export.toString(), row.getObject("recommendation_id", UUID.class).toString(),
                    previewId.toString(), approvalId.toString(), basis,
                    row.getString("export_fingerprint"), files(transaction, previewId), generatedAt);
                UUID reportId = row.getObject("latest_report_id", UUID.class);
                ConfigurationHistoryReadModel.UserReport report = reportId == null ? null
                    : new ConfigurationHistoryReadModel.UserReport(reportId.toString(),
                        export.toString(), ConfigurationUserReport.ReportedState.valueOf(
                            row.getString("reported_state")),
                        row.getString("reason_code") == null ? null
                            : ConfigurationUserReport.ReasonCode.valueOf(row.getString("reason_code")),
                        ConfigurationUserReport.Source.USER,
                        row.getTimestamp("reported_at").toInstant());
                ConfigurationHistoryReadModel.project(history, basis, report);
                return new Snapshot(history, report);
            }
        }
    }

    private static List<ConfigurationGenerationHistory.FileMetadata> files(
        Connection transaction, UUID previewId) throws SQLException {
        List<ConfigurationGenerationHistory.FileMetadata> files = new ArrayList<>();
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT relative_path, action, after_hash FROM config_preview_file "
                + "WHERE preview_id = ? ORDER BY relative_path FOR SHARE")) {
            query.setObject(1, previewId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    files.add(new ConfigurationGenerationHistory.FileMetadata(rows.getString(1),
                        PreviewFile.Action.valueOf(rows.getString(2)), rows.getString(3)));
                }
            }
        }
        return List.copyOf(files);
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("a database ID is not a UUID", exception);
        }
    }
}
