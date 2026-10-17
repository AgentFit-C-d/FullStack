package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

/** Updates B9's latest USER statement; A must append the historical Audit event in this transaction. */
public final class JdbcConfigurationReportWriter {
    public ConfigurationHistoryReadModel.UserReport save(Connection transaction, String exportId,
        UUID actorId, String reportId, ConfigurationUserReport.Accepted accepted) throws SQLException {
        Objects.requireNonNull(transaction);
        Objects.requireNonNull(accepted);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("report requires a caller-owned transaction");
        }
        UUID export = uuid(exportId);
        if (actorId == null) throw new IllegalArgumentException("invalid authenticated actor ID");
        UUID report = uuid(reportId);
        if (accepted.reportedAt() == null || accepted.reportedState() == null
            || accepted.reportedState() == ConfigurationUserReport.ReportedState.APPLIED
                && accepted.reasonCode() != null
            || accepted.reportedState() == ConfigurationUserReport.ReportedState.FAILED
                && accepted.reasonCode() == null) {
            throw new IllegalArgumentException("invalid USER report");
        }

        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT generated_at, reported_at FROM config_export WHERE id = ? FOR UPDATE")) {
            query.setObject(1, export);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("export no longer exists");
                Timestamp previous = row.getTimestamp("reported_at");
                if (accepted.reportedAt().isBefore(row.getTimestamp("generated_at").toInstant())
                    || previous != null && !accepted.reportedAt().isAfter(previous.toInstant())) {
                    throw new IllegalArgumentException("report time is not later than export and latest report");
                }
            }
        }
        try (PreparedStatement update = transaction.prepareStatement(
            "UPDATE config_export SET latest_report_id = ?, reported_state = ?, reason_code = ?, "
                + "reported_by = ?, reported_at = ? WHERE id = ?")) {
            update.setObject(1, report);
            update.setString(2, accepted.reportedState().name());
            update.setString(3, accepted.reasonCode() == null ? null : accepted.reasonCode().name());
            update.setObject(4, actorId);
            update.setTimestamp(5, Timestamp.from(accepted.reportedAt()));
            update.setObject(6, export);
            if (update.executeUpdate() != 1) {
                throw new IllegalStateException("USER report was not saved");
            }
        }
        return new ConfigurationHistoryReadModel.UserReport(report.toString(), export.toString(),
            accepted.reportedState(), accepted.reasonCode(), accepted.source(), accepted.reportedAt());
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("a database ID is not a UUID", exception);
        }
    }
}
