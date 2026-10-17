package com.agentfit.coreapi.configuration.api;

import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryPageService;
import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationHistoryReader;
import com.agentfit.coreapi.persistence.ProjectDbId;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reads project-scoped, content-free history against one consistent current basis. */
@Service
public class ConfigurationHistoryRuntimeService {
    private final DataSource dataSource;
    private final RecommendationCreationService.OwnerCheckedSnapshotReader snapshots;
    private final ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs;
    private final JdbcConfigurationHistoryReader reader = new JdbcConfigurationHistoryReader();

    public ConfigurationHistoryRuntimeService(DataSource dataSource,
            RecommendationCreationService.OwnerCheckedSnapshotReader snapshots,
            ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs) {
        this.dataSource = dataSource;
        this.snapshots = snapshots;
        this.catalogs = catalogs;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ConfigurationHistoryPageService.Page list(String projectId,
            ConfigurationHistoryPageService.Cursor after, int limit) {
        String project = ProjectDbId.require(projectId);
        var catalog = catalogs.getIfAvailable();
        if (catalog == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "approved Catalog is unavailable");
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            var pages = new ConfigurationHistoryPageService(
                ignored -> ConfigurationApprovalService.basis(snapshots.load(project), catalog.current()),
                (ignored, cursor, requested) -> read(connection, project, cursor, requested));
            return pages.list(project, after, limit);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private ConfigurationHistoryPageService.StoredPage read(Connection connection, String project,
            ConfigurationHistoryPageService.Cursor after, int limit) {
        try {
            var cursor = after == null ? null
                : new JdbcConfigurationHistoryReader.Cursor(after.generatedAt(), after.generationId());
            var stored = reader.list(connection, project, cursor, limit);
            var items = stored.items().stream().map(item ->
                new ConfigurationHistoryPageService.StoredItem(item.generation(),
                    item.latestUserReport())).toList();
            var next = stored.nextCursor() == null ? null
                : new ConfigurationHistoryPageService.Cursor(stored.nextCursor().generatedAt(),
                    stored.nextCursor().generationId());
            return new ConfigurationHistoryPageService.StoredPage(items, next);
        } catch (SQLException exception) {
            throw new IllegalStateException("configuration history read failed", exception);
        }
    }
}
