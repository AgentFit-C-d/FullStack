package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.persistence.JdbcLatestRecommendationReader;
import java.sql.SQLException;
import java.util.HashMap;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Restores current A environment/basis and B recommendation facts for Preview. */
@Component
public final class JdbcPreviewContextAdapter implements PreviewCreationService.TrustedContextReader {
    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final JdbcLatestRecommendationReader recommendations = new JdbcLatestRecommendationReader();
    private final com.agentfit.coreapi.recommendation.persistence.JdbcPreviewRecommendationContextReader contextReader =
        new com.agentfit.coreapi.recommendation.persistence.JdbcPreviewRecommendationContextReader();

    public JdbcPreviewContextAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public PreviewCreationService.TrustedContext load(String projectId, String recommendationId) {
        return transactions.execute(status -> {
            var connection = DataSourceUtils.getConnection(dataSource);
            try {
                var stored = recommendations.byId(connection, projectId, recommendationId);
                if (stored == null) return null;
                var current = connection.prepareStatement("SELECT version FROM project WHERE id = ? FOR SHARE");
                current.setObject(1, java.util.UUID.fromString(projectId));
                try (current; var row = current.executeQuery()) {
                    if (!row.next() || row.getLong(1) != stored.basis().projectVersion()) return null;
                }
                var targetQuery = connection.prepareStatement("SELECT os_family, client_id, client_version, id FROM environment_profile WHERE project_id = ?");
                targetQuery.setObject(1, java.util.UUID.fromString(projectId));
                EnvironmentTarget target;
                Object environmentId;
                try (targetQuery; var row = targetQuery.executeQuery()) {
                    if (!row.next()) return null;
                    target = new EnvironmentTarget(row.getString(1), row.getString(2), row.getString(3));
                    environmentId = row.getObject(4);
                }
                var installed = new HashMap<String, String>();
                var installedQuery = connection.prepareStatement("SELECT normalized_name, version FROM environment_installed_component WHERE environment_profile_id = ?");
                installedQuery.setObject(1, environmentId);
                try (installedQuery; var rows = installedQuery.executeQuery()) {
                    while (rows.next()) installed.put(rows.getString(1), rows.getString(2));
                }
                return contextReader.byId(connection, projectId, recommendationId, stored.basis(), target, installed);
            } catch (SQLException | RuntimeException exception) {
                throw new IllegalStateException("Preview context read failed", exception);
            }
        });
    }
}
