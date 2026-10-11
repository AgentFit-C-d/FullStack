package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.recommendation.persistence.JdbcLatestRecommendationReader;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.sql.Connection;
import java.sql.SQLException;

/** Reads B's saved download inputs in A's authenticated, project-locked transaction. */
public final class JdbcConfigurationContextReader {
    public record Snapshot(StoredRecommendationState recommendation,
                           StoredPreviewState preview, StoredApprovalState approval) {}

    private final JdbcStoredPreviewReader previewReader = new JdbcStoredPreviewReader();
    private final JdbcLatestRecommendationReader recommendationReader =
        new JdbcLatestRecommendationReader();

    public Snapshot byPreviewId(Connection transaction, String projectId, String previewId)
        throws SQLException {
        return read(transaction, projectId,
            previewReader.byPreviewId(transaction, projectId, previewId));
    }

    public Snapshot byApprovalId(Connection transaction, String projectId, String approvalId)
        throws SQLException {
        return read(transaction, projectId,
            previewReader.byApprovalId(transaction, projectId, approvalId));
    }

    private Snapshot read(Connection transaction, String projectId,
                          JdbcStoredPreviewReader.Snapshot stored) throws SQLException {
        if (stored == null) return null;
        StoredRecommendationState recommendation = recommendationReader.byId(
            transaction, projectId, stored.recommendationId());
        if (recommendation == null || !recommendation.basis().equals(stored.preview().basis())) {
            throw new IllegalStateException("Preview recommendation basis does not match");
        }
        return new Snapshot(recommendation, stored.preview(), stored.approval());
    }
}
