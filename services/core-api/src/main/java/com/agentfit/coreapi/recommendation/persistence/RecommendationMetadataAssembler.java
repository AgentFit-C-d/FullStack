package com.agentfit.coreapi.recommendation.persistence;

import com.agentfit.coreapi.catalog.CatalogRecommendationDetails;
import com.agentfit.coreapi.catalog.CatalogRecommendationWorkflow;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Projects B's validated internal outcome into B1-B3 metadata; B4 text remains a caller contract. */
public final class RecommendationMetadataAssembler {
    private RecommendationMetadataAssembler() {}

    public static JdbcRecommendationMetadataWriter.Draft assemble(
        String recommendationId, PreviewBasis basis, CatalogRecommendationWorkflow.Result result,
        JdbcRecommendationMetadataWriter.EvidenceLevel evidenceLevel, Instant createdAt,
        List<JdbcRecommendationMetadataWriter.Notice> notices, Supplier<String> itemIds) {
        Objects.requireNonNull(basis);
        Objects.requireNonNull(result);
        Objects.requireNonNull(result.decision());
        Objects.requireNonNull(itemIds);
        if (!basis.catalogReleaseId().equals(result.catalogReleaseId())
            || !basis.catalogHash().equals(result.catalogHash())) {
            throw new IllegalArgumentException("recommendation Catalog differs from stored basis");
        }
        RecommendationDecision decision = result.decision();
        Set<String> decisionKeys = new HashSet<>(decision.toolKeys());
        Set<String> detailKeys = new HashSet<>();
        List<JdbcRecommendationMetadataWriter.Item> items = new ArrayList<>();
        for (CatalogRecommendationDetails.Item item : result.items()) {
            if (item == null || !detailKeys.add(item.toolKey())
                || item.supportEvidence() == null) {
                throw new IllegalArgumentException("invalid verified recommendation item");
            }
            items.add(new JdbcRecommendationMetadataWriter.Item(itemIds.get(), item.toolKey(),
                item.catalogVersion(), item.supportKey(), true,
                "Verified Catalog support in the selected environment",
                JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE));
        }
        if (decisionKeys.size() != decision.toolKeys().size()
            || !detailKeys.equals(decisionKeys)
            || decision.status() != RecommendationDecision.Status.RECOMMENDED
                && !items.isEmpty()) {
            throw new IllegalArgumentException("recommendation items differ from decision");
        }
        List<JdbcRecommendationMetadataWriter.Capability> capabilities = new ArrayList<>();
        for (AiCapabilityIntake.Claim claim : result.capabilities()) {
            if (claim == null || claim.need() == null) {
                throw new IllegalArgumentException("invalid Capability claim");
            }
            capabilities.add(new JdbcRecommendationMetadataWriter.Capability(
                claim.capabilityKey(),
                JdbcRecommendationMetadataWriter.Necessity.valueOf(claim.need().name()),
                claim.reason(), claim.sourceFields()));
        }
        var draft = new JdbcRecommendationMetadataWriter.Draft(recommendationId, basis,
            decision.status(), evidenceLevel, createdAt, List.copyOf(capabilities),
            List.copyOf(items), notices);
        JdbcRecommendationMetadataWriter.validate(draft);
        return draft;
    }
}
