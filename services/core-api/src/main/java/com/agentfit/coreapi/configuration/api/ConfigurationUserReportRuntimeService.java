package com.agentfit.coreapi.configuration.api;

import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationHistoryReader;
import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationReportWriter;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReportService;
import com.agentfit.coreapi.persistence.ProjectDbId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Persists a user's application statement and its Audit in one project-owned transaction. */
@Service
public class ConfigurationUserReportRuntimeService {
    private final DataSource dataSource;
    private final Clock clock;
    private final JdbcConfigurationHistoryReader history = new JdbcConfigurationHistoryReader();
    private final JdbcConfigurationReportWriter writer = new JdbcConfigurationReportWriter();

    public ConfigurationUserReportRuntimeService(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Transactional
    public ConfigurationHistoryReadModel.UserReport report(String projectId, String exportId,
            ConfigurationUserReport.Submission submission, UUID actorId) {
        if (actorId == null || submission == null) {
            throw new IllegalArgumentException("authenticated USER report required");
        }
        String project = ProjectDbId.require(projectId);
        String export = ProjectDbId.require(exportId);
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ConfigurationApprovalService.lockOwner(connection, project, actorId);
            var service = new ConfigurationUserReportService(
                (projectKey, generationKey) -> {
                    var stored = find(connection, projectKey, generationKey);
                    return new ConfigurationUserReportService.TrustedGeneration(projectKey,
                        generationKey, stored.generation().createdAt());
                },
                () -> UUID.randomUUID().toString(), clock,
                (generation, accepted, reportId) -> {
                    var current = find(connection, generation.projectId(), generation.generationId());
                    if (!generation.createdAt().equals(current.generation().createdAt())) {
                        throw new IllegalStateException("generation changed before report");
                    }
                    try {
                        var saved = writer.save(connection, generation.generationId(), actorId,
                            reportId, accepted);
                        audit(connection, project, actorId, generation.generationId());
                        return saved;
                    } catch (SQLException exception) {
                        throw new IllegalStateException("USER report save failed", exception);
                    }
                });
            return service.report(project, export, submission);
        } catch (SQLException exception) {
            throw new IllegalStateException("USER report read failed", exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private JdbcConfigurationHistoryReader.Snapshot find(Connection connection,
            String projectId, String exportId) {
        try {
            var stored = history.find(connection, projectId, exportId);
            if (stored == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "generation not found");
            }
            return stored;
        } catch (SQLException exception) {
            throw new IllegalStateException("USER report generation read failed", exception);
        }
    }

    private static void audit(Connection connection, String projectId, UUID actorId,
            String exportId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO audit_event (id, project_id, actor_id, event, outcome, "
                    + "target_type, target_id, occurred_at) VALUES (?, ?, ?, "
                    + "'CONFIGURATION_APPLICATION_REPORTED', 'SUCCEEDED', 'CONFIG_EXPORT', ?, "
                    + "CURRENT_TIMESTAMP)")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, ProjectDbId.uuid(projectId));
            insert.setObject(3, actorId);
            insert.setObject(4, UUID.fromString(exportId));
            insert.executeUpdate();
        }
    }
}
