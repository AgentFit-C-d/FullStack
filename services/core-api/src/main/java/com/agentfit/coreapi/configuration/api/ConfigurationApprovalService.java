package com.agentfit.coreapi.configuration.api;

import com.agentfit.coreapi.catalog.ApprovalCreationService;
import com.agentfit.coreapi.catalog.CatalogSemanticParser;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.catalog.StoredPreviewCoverage;
import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationContextReader;
import com.agentfit.coreapi.configuration.persistence.JdbcPreviewApprovalWriter;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.persistence.ProjectDbId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** B finalization inside one project-owned PostgreSQL transaction. */
@Service
public class ConfigurationApprovalService {
    public record Result(String approvalId, String previewId, String fingerprint,
                         Instant approvedAt, Instant expiresAt,
                         List<String> unmetCapabilityKeys) {
        public Result { unmetCapabilityKeys = List.copyOf(unmetCapabilityKeys); }
    }

    private final DataSource dataSource;
    private final RecommendationCreationService.OwnerCheckedSnapshotReader snapshots;
    private final ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs;
    private final Clock clock;
    private final JdbcConfigurationContextReader contexts = new JdbcConfigurationContextReader();
    private final JdbcPreviewApprovalWriter writer = new JdbcPreviewApprovalWriter();

    public ConfigurationApprovalService(DataSource dataSource,
            RecommendationCreationService.OwnerCheckedSnapshotReader snapshots,
            ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs,
            Clock clock) {
        this.dataSource = dataSource;
        this.snapshots = snapshots;
        this.catalogs = catalogs;
        this.clock = clock;
    }

    @Transactional
    public Result approve(String projectId, String previewId, String fingerprint,
                          boolean confirmed, UUID actorId) {
        if (actorId == null) throw new IllegalArgumentException("authenticated UUID actor required");
        String project = ProjectDbId.require(projectId);
        var catalog = catalogs.getIfAvailable();
        if (catalog == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "approved Catalog is unavailable");
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            var approved = catalog.current();
            var current = snapshots.load(project);
            PreviewBasis basis = basis(current, approved);
            var saved = contexts.byPreviewId(connection, project, previewId);
            if (saved == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Preview not found");
            var service = new ApprovalCreationService(
                (projectKey, previewKey) -> new ApprovalCreationService.TrustedContext(
                    basis, saved.preview()), catalog, () -> UUID.randomUUID().toString(), clock,
                (context, issued) -> {
                    try {
                        lockOwner(connection, project, actorId);
                        if (!basis.equals(basis(snapshots.load(project), approved))) {
                            throw new IllegalStateException("project changed before approval");
                        }
                        var refreshed = contexts.byPreviewId(connection, project, previewId);
                        if (refreshed == null || !saved.preview().equals(refreshed.preview())
                            || refreshed.approval() != null) {
                            throw new IllegalStateException("Preview changed before approval");
                        }
                        var stored = writer.approve(connection, actorId, refreshed.preview(), issued);
                        audit(connection, project, actorId, stored.previewId());
                        return stored;
                    } catch (SQLException exception) {
                        throw new IllegalStateException("approval save failed", exception);
                    }
                });
            var result = service.approve(project, previewId, fingerprint, confirmed);
            var parsed = CatalogSemanticParser.load(approved.directory());
            var warnings = StoredPreviewCoverage.evaluate(saved, parsed, current.environment(),
                current.installedToolVersions()).unmetCapabilityKeys();
            return new Result(result.approvalId(), result.previewId(), result.fingerprint(),
                result.approvedAt(), result.expiresAt(), warnings);
        } catch (SQLException exception) {
            throw new IllegalStateException("approval read failed", exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    static PreviewBasis basis(RecommendationCreationService.OwnerCheckedSnapshot snapshot,
                             RecommendationCreationService.ApprovedCatalog approved) {
        if (snapshot == null || approved == null) {
            throw new IllegalArgumentException("current project snapshot is missing");
        }
        var catalog = CatalogSemanticParser.load(approved.directory());
        if (!catalog.catalogHash().equals(approved.approvedHash())) {
            throw new IllegalStateException("Catalog approval changed");
        }
        return new PreviewBasis(snapshot.projectId(), snapshot.projectVersion(),
            snapshot.confirmedProfileId(), snapshot.confirmedProfileVersion(),
            snapshot.confirmationEventId(), snapshot.reviewVersion(),
            snapshot.developerVersion(), snapshot.environmentVersion(),
            catalog.release().releaseId(), approved.approvedHash());
    }

    static void lockOwner(Connection connection, String projectId, UUID actorId)
        throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT user_id FROM project WHERE id = ? FOR UPDATE")) {
            query.setObject(1, ProjectDbId.uuid(projectId));
            try (ResultSet row = query.executeQuery()) {
                if (!row.next() || !actorId.equals(row.getObject(1, UUID.class))) {
                    throw new ProjectNotFoundException();
                }
            }
        }
    }

    private static void audit(Connection connection, String projectId, UUID actorId,
                              String previewId) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO audit_event (id, project_id, actor_id, event, outcome, "
                    + "target_type, target_id, occurred_at) "
                    + "VALUES (?, ?, ?, 'PREVIEW_APPROVED', 'SUCCEEDED', 'PREVIEW', ?, CURRENT_TIMESTAMP)")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, ProjectDbId.uuid(projectId));
            insert.setObject(3, actorId);
            insert.setObject(4, UUID.fromString(previewId));
            insert.executeUpdate();
        }
    }
}
