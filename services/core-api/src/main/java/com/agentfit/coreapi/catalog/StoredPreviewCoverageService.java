package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.export.PreviewFreshnessGate;
import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationContextReader;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** A-owned read port for showing an unchanged partial Preview warning after page reload. */
public final class StoredPreviewCoverageService {
    /** A authenticates and loads the current basis and saved B context in one owned read. */
    @FunctionalInterface
    public interface TrustedPreviewReader {
        TrustedContext load(String projectId, String previewId);
    }

    public record TrustedContext(PreviewBasis currentBasis, EnvironmentTarget currentTarget,
                                 Map<String, String> installedToolVersions,
                                 JdbcConfigurationContextReader.Snapshot saved) {
        public TrustedContext {
            installedToolVersions = Collections.unmodifiableMap(
                new HashMap<>(Objects.requireNonNull(installedToolVersions)));
        }
    }

    private final TrustedPreviewReader previews;
    private final RecommendationCreationService.ApprovedCatalogReader catalogs;
    private final Clock clock;

    public StoredPreviewCoverageService(TrustedPreviewReader previews,
                                        RecommendationCreationService.ApprovedCatalogReader catalogs,
                                        Clock clock) {
        this.previews = Objects.requireNonNull(previews);
        this.catalogs = Objects.requireNonNull(catalogs);
        this.clock = Objects.requireNonNull(clock);
    }

    public PreviewCapabilityCoverage.Result coverage(String projectId, String previewId) {
        if (projectId == null || projectId.isBlank()
            || previewId == null || previewId.isBlank()) {
            throw new IllegalArgumentException("project and Preview required");
        }
        TrustedContext context = Objects.requireNonNull(previews.load(projectId, previewId));
        if (context.currentBasis() == null
            || !projectId.equals(context.currentBasis().projectId())
            || context.saved() == null || context.saved().preview() == null
            || !previewId.equals(context.saved().preview().previewId())) {
            throw new IllegalArgumentException("Preview context belongs to another project");
        }
        PreviewFreshnessGate.requireStoredCurrent(context.saved().preview(),
            context.currentBasis(), context.saved().preview().fingerprint(), clock.instant());
        if (context.currentTarget() == null || !context.currentTarget().complete()) {
            throw new IllegalArgumentException("current Preview target is incomplete");
        }
        var approved = Objects.requireNonNull(catalogs.current());
        ParsedCatalog parsed = CatalogSemanticParser.load(approved.directory());
        CatalogReadService.list(parsed, approved.approvedHash(),
            new CatalogReadService.Filter(context.currentTarget().osFamily(),
                context.currentTarget().clientId(),
                context.currentTarget().clientVersion(), null));
        return StoredPreviewCoverage.evaluate(context.saved(), parsed,
            context.currentTarget(), context.installedToolVersions());
    }
}
