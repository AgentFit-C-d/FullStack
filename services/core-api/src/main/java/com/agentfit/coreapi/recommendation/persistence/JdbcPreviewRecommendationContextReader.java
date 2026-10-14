package com.agentfit.coreapi.recommendation.persistence;

import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Restores B's exact recommendation/B2 Preview facts within A's owner-checked transaction. */
public final class JdbcPreviewRecommendationContextReader {
    private final JdbcLatestRecommendationReader recommendations = new JdbcLatestRecommendationReader();

    public PreviewCreationService.TrustedContext byId(Connection transaction, String projectId,
                                                      String recommendationId,
                                                      PreviewBasis currentBasis,
                                                      EnvironmentTarget currentTarget,
                                                      Map<String, String> trustedInstalledVersions)
        throws SQLException {
        if (currentBasis == null || !Objects.equals(projectId, currentBasis.projectId())
            || currentTarget == null || !currentTarget.complete()
            || trustedInstalledVersions == null) {
            throw new IllegalArgumentException("incomplete owner-checked Preview context");
        }
        JdbcRecommendationMetadataWriter.Draft stored = recommendations.byIdMetadata(
            transaction, projectId, recommendationId);
        if (stored == null) return null;
        JdbcRecommendationMetadataWriter.requireValid(stored);
        if (stored.status() != RecommendationDecision.Status.RECOMMENDED) {
            throw new IllegalArgumentException("stored recommendation is not Preview eligible");
        }
        Set<String> required = stored.capabilities().stream()
            .filter(capability -> capability.necessity()
                == JdbcRecommendationMetadataWriter.Necessity.REQUIRED)
            .map(JdbcRecommendationMetadataWriter.Capability::key)
            .collect(Collectors.toUnmodifiableSet());
        var recommendation = new StoredRecommendationState(stored.id(), stored.basis(),
            stored.status(), stored.items().stream()
                .map(JdbcRecommendationMetadataWriter.Item::toolKey).toList());
        var capabilities = new PreviewCreationService.RequiredCapabilities(stored.id(),
            stored.basis(), required);
        return new PreviewCreationService.TrustedContext(currentBasis, currentTarget,
            recommendation, trustedInstalledVersions, capabilities,
            stored.items().stream().map(item -> new PreviewCreationService.StoredItemEvidence(
                item.toolKey(), item.toolVersion(), item.supportKey(), item.required())).toList());
    }
}
