package com.agentfit.coreapi.configuration.api;

import com.agentfit.coreapi.catalog.CatalogPreviewRequest;
import com.agentfit.coreapi.catalog.CatalogSemanticParser;
import com.agentfit.coreapi.catalog.ConfigurationDownloadService;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.catalog.StoredPreviewCoverage;
import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationContextReader;
import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationExportWriter;
import com.agentfit.coreapi.persistence.ProjectDbId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Regenerates approved files and stores only content-free export history. */
@Service
public class ConfigurationDownloadRuntimeService {
    public record Result(String exportId, byte[] zipBytes, List<String> unmetCapabilityKeys) {
        public Result {
            zipBytes = Arrays.copyOf(zipBytes, zipBytes.length);
            unmetCapabilityKeys = List.copyOf(unmetCapabilityKeys);
        }
        @Override public byte[] zipBytes() { return Arrays.copyOf(zipBytes, zipBytes.length); }
    }

    private final DataSource dataSource;
    private final RecommendationCreationService.OwnerCheckedSnapshotReader snapshots;
    private final ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs;
    private final Clock clock;
    private final JdbcConfigurationContextReader contexts = new JdbcConfigurationContextReader();
    private final JdbcConfigurationExportWriter writer = new JdbcConfigurationExportWriter();

    public ConfigurationDownloadRuntimeService(DataSource dataSource,
            RecommendationCreationService.OwnerCheckedSnapshotReader snapshots,
            ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs,
            Clock clock) {
        this.dataSource = dataSource;
        this.snapshots = snapshots;
        this.catalogs = catalogs;
        this.clock = clock;
    }

    @Transactional
    public Result download(String projectId, String approvalId, String previewId,
                           String fingerprint, CatalogPreviewRequest request, UUID actorId) {
        if (actorId == null || request == null) {
            throw new IllegalArgumentException("authenticated download request required");
        }
        String project = ProjectDbId.require(projectId);
        var catalog = catalogs.getIfAvailable();
        if (catalog == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "approved Catalog is unavailable");
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            var approved = catalog.current();
            var current = snapshots.load(project);
            var basis = ConfigurationApprovalService.basis(current, approved);
            var saved = contexts.byApprovalId(connection, project, approvalId);
            if (saved == null || saved.approval() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "approval not found");
            }
            var service = new ConfigurationDownloadService(
                (projectKey, approvalKey) -> new ConfigurationDownloadService.TrustedContext(
                    basis, current.environment(), saved.recommendation(), saved.preview(),
                    saved.approval(), current.installedToolVersions(), saved.storedItems()),
                catalog, () -> UUID.randomUUID().toString(), clock,
                (context, history) -> {
                    try {
                        ConfigurationApprovalService.lockOwner(connection, project, actorId);
                        if (!basis.equals(ConfigurationApprovalService.basis(
                            snapshots.load(project), approved))) {
                            throw new IllegalStateException("project changed before download");
                        }
                        var refreshed = contexts.byApprovalId(connection, project, approvalId);
                        if (refreshed == null || !saved.preview().equals(refreshed.preview())
                            || !saved.approval().equals(refreshed.approval())) {
                            throw new IllegalStateException("approval changed before download");
                        }
                        writer.save(connection, history);
                        audit(connection, project, actorId, history.id());
                        return history.id();
                    } catch (SQLException exception) {
                        throw new IllegalStateException("export save failed", exception);
                    }
                });
            var generated = service.download(project, approvalId, previewId, fingerprint, request);
            var warnings = StoredPreviewCoverage.evaluate(saved,
                CatalogSemanticParser.load(approved.directory()), current.environment(),
                current.installedToolVersions()).unmetCapabilityKeys();
            return new Result(generated.history().id(), generated.zipBytes(), warnings);
        } catch (SQLException exception) {
            throw new IllegalStateException("download read failed", exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static void audit(Connection connection, String projectId, UUID actorId,
                              String exportId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO audit_event (id, project_id, actor_id, event, outcome, "
                    + "target_type, target_id, occurred_at) "
                    + "VALUES (?, ?, ?, 'CONFIG_DOWNLOADED', 'SUCCEEDED', 'CONFIG_EXPORT', ?, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, ProjectDbId.uuid(projectId));
            insert.setObject(3, actorId);
            insert.setObject(4, UUID.fromString(exportId));
            insert.executeUpdate();
        }
    }
}
