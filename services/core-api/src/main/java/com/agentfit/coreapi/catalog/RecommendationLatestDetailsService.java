package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Restores B1-B4 details without AI and enriches only a current result from the approved Catalog. */
public final class RecommendationLatestDetailsService {
    /** A reads one owner-checked, consistent current basis and environment target. */
    public record Current(PreviewBasis basis, EnvironmentTarget target) {}

    @FunctionalInterface
    public interface OwnerCheckedCurrentReader {
        Current load(String projectId);
    }

    /** A reads the latest B1-B4 metadata scoped to the same owned project and transaction. */
    @FunctionalInterface
    public interface StoredMetadataReader {
        JdbcRecommendationMetadataWriter.Draft latest(String projectId);
    }

    public record Snapshot(RecommendationReadModel summary,
                           JdbcRecommendationMetadataWriter.EvidenceLevel evidenceLevel,
                           Instant createdAt,
                           List<JdbcRecommendationMetadataWriter.Capability> capabilities,
                           List<JdbcRecommendationMetadataWriter.Item> storedItems,
                           List<JdbcRecommendationMetadataWriter.Notice> notices,
                           List<CatalogRecommendationDetails.Item> catalogItems) {
        public Snapshot {
            capabilities = List.copyOf(capabilities);
            storedItems = List.copyOf(storedItems);
            notices = List.copyOf(notices);
            catalogItems = List.copyOf(catalogItems);
        }
    }

    private final OwnerCheckedCurrentReader currentReader;
    private final StoredMetadataReader metadataReader;
    private final RecommendationCreationService.ApprovedCatalogReader catalogs;

    public RecommendationLatestDetailsService(OwnerCheckedCurrentReader currentReader,
                                              StoredMetadataReader metadataReader,
                                              RecommendationCreationService.ApprovedCatalogReader catalogs) {
        this.currentReader = Objects.requireNonNull(currentReader);
        this.metadataReader = Objects.requireNonNull(metadataReader);
        this.catalogs = Objects.requireNonNull(catalogs);
    }

    public Snapshot latest(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("project ID required");
        }
        Current current = Objects.requireNonNull(currentReader.load(projectId));
        if (current.basis() == null || !projectId.equals(current.basis().projectId())) {
            throw new IllegalArgumentException("current basis belongs to another project");
        }
        RecommendationReadModel.latest(null, current.basis());
        var stored = metadataReader.latest(projectId);
        if (stored == null) return null;
        List<String> keys = stored.items().stream().map(JdbcRecommendationMetadataWriter.Item::toolKey).toList();
        var summary = RecommendationReadModel.latest(new StoredRecommendationState(
            stored.id(), stored.basis(), stored.status(), keys), current.basis());
        List<CatalogRecommendationDetails.Item> details = List.of();
        if (summary.validity() == RecommendationReadModel.Validity.CURRENT
            && summary.status() == RecommendationDecision.Status.RECOMMENDED) {
            details = currentCatalogDetails(stored, current);
        }
        return new Snapshot(summary, stored.evidenceLevel(), stored.createdAt(),
            stored.capabilities(), stored.items(), stored.notices(), details);
    }

    private List<CatalogRecommendationDetails.Item> currentCatalogDetails(
        JdbcRecommendationMetadataWriter.Draft stored, Current current) {
        if (current.target() == null || !current.target().complete()) {
            throw new IllegalArgumentException("current recommendation target is incomplete");
        }
        var approved = Objects.requireNonNull(catalogs.current());
        if (!stored.basis().catalogHash().equals(approved.approvedHash())) {
            throw new CatalogBundleLoader.CatalogUnavailableException("stored Catalog is not approved");
        }
        ParsedCatalog parsed = CatalogSemanticParser.load(approved.directory());
        CatalogReadService.list(parsed, approved.approvedHash(), new CatalogReadService.Filter(
            current.target().osFamily(), current.target().clientId(),
            current.target().clientVersion(), null));
        if (!stored.basis().catalogReleaseId().equals(parsed.release().releaseId())) {
            throw new CatalogBundleLoader.CatalogUnavailableException("stored Catalog release changed");
        }
        var required = stored.capabilities().stream()
            .filter(claim -> claim.necessity() == JdbcRecommendationMetadataWriter.Necessity.REQUIRED)
            .map(JdbcRecommendationMetadataWriter.Capability::key).collect(Collectors.toUnmodifiableSet());
        var decision = new RecommendationDecision(stored.status(),
            stored.items().stream().map(JdbcRecommendationMetadataWriter.Item::toolKey).toList(),
            List.of(), List.of());
        var details = CatalogRecommendationDetails.project(parsed, decision, required, current.target());
        Map<String, JdbcRecommendationMetadataWriter.Item> saved = stored.items().stream()
            .collect(Collectors.toMap(JdbcRecommendationMetadataWriter.Item::toolKey, Function.identity()));
        if (details.size() != saved.size() || details.stream().anyMatch(detail -> {
            var item = saved.get(detail.toolKey());
            return item == null || !item.required()
                || !item.toolVersion().equals(detail.catalogVersion())
                || !item.supportKey().equals(detail.supportKey());
        })) {
            throw new IllegalArgumentException("stored recommendation differs from approved Catalog");
        }
        return details;
    }
}
