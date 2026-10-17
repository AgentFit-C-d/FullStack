package com.agentfit.coreapi.recommendation.persistence;

import com.agentfit.coreapi.catalog.CatalogBundleLoader;
import com.agentfit.coreapi.catalog.CatalogSemanticParser;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.persistence.ProjectDbId;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

/** Reads A's current snapshot and B's saved result inside the latest service's transaction. */
@Component
public final class JdbcLatestRecommendationPorts implements
    RecommendationLatestDetailsService.OwnerCheckedCurrentReader,
    RecommendationLatestDetailsService.StoredMetadataReader {
    private final RecommendationCreationService.OwnerCheckedSnapshotReader snapshots;
    private final ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs;
    private final DataSource dataSource;
    private final JdbcLatestRecommendationReader stored = new JdbcLatestRecommendationReader();

    public JdbcLatestRecommendationPorts(
        RecommendationCreationService.OwnerCheckedSnapshotReader snapshots,
        ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs,
        DataSource dataSource) {
        this.snapshots = snapshots;
        this.catalogs = catalogs;
        this.dataSource = dataSource;
    }

    @Override
    public RecommendationLatestDetailsService.Current load(String projectId) {
        String canonicalProjectId = ProjectDbId.require(projectId);
        var snapshot = snapshots.load(canonicalProjectId);
        if (snapshot == null || !canonicalProjectId.equals(snapshot.projectId())) {
            throw new IllegalArgumentException("current recommendation project is missing");
        }
        var approved = catalogs.getObject().current();
        var catalog = CatalogSemanticParser.load(approved.directory());
        if (!catalog.catalogHash().equals(approved.approvedHash())) {
            throw new CatalogBundleLoader.CatalogUnavailableException("Catalog hash is not approved");
        }
        return new RecommendationLatestDetailsService.Current(new PreviewBasis(
            snapshot.projectId(), snapshot.projectVersion(), snapshot.confirmedProfileId(),
            snapshot.confirmedProfileVersion(), snapshot.confirmationEventId(),
            snapshot.reviewVersion(), snapshot.developerVersion(), snapshot.environmentVersion(),
            catalog.release().releaseId(), approved.approvedHash()), snapshot.environment());
    }

    @Override
    public JdbcRecommendationMetadataWriter.Draft latest(String projectId) {
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            return stored.latestMetadata(connection, projectId);
        } catch (SQLException exception) {
            throw new IllegalStateException("latest recommendation read failed", exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
