package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationContextReader;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoredPreviewCoverageTest {
    private static final EnvironmentTarget TARGET = new EnvironmentTarget("WINDOWS", "claude-code", "1.0");
    private static final String DOCUMENT = "cap_document_reference";
    private static final String BROWSER = "cap_browser_verification";
    private static final String HASH = "a".repeat(64);

    @Test
    void reconstructedPartialPreviewKeepsWarningUntilExactInstalledVersionCoversIt() {
        var context = context();
        var catalog = catalog(HASH);

        assertEquals(List.of(BROWSER), StoredPreviewCoverage.evaluate(context, catalog,
            TARGET, Map.of()).unmetCapabilityKeys());
        assertTrue(StoredPreviewCoverage.evaluate(context, catalog, TARGET,
            Map.of("browser", "1.0")).complete());
        assertEquals(List.of(BROWSER), StoredPreviewCoverage.evaluate(context, catalog,
            TARGET, Map.of("browser", "0.9")).unmetCapabilityKeys());
    }

    @Test
    void rejectsCatalogThatDiffersFromSavedPreviewBasis() {
        assertThrows(IllegalArgumentException.class, () -> StoredPreviewCoverage.evaluate(
            context(), catalog("b".repeat(64)), TARGET, Map.of()));
    }

    private static JdbcConfigurationContextReader.Snapshot context() {
        var basis = new PreviewBasis("project-1", 1, "profile-1", 1, "event-1", 0, 0, 1,
            "test", HASH);
        var recommendation = new StoredRecommendationState("recommendation-1", basis,
            RecommendationDecision.Status.RECOMMENDED, List.of("docs", "browser"));
        var preview = new StoredPreviewState("preview-1", basis, "fingerprint",
            Instant.EPOCH, Instant.EPOCH.plusSeconds(600));
        return new JdbcConfigurationContextReader.Snapshot(recommendation, preview, null,
            List.of(), Set.of(DOCUMENT, BROWSER), List.of("docs"));
    }

    private static ParsedCatalog catalog(String hash) {
        return new ParsedCatalog(hash, new CatalogRelease("test", Map.of(
            "docs", tool("docs", DOCUMENT), "browser", tool("browser", BROWSER)),
            Set.of(new VerifiedCombination(Set.of("docs", "browser"), TARGET))),
            List.of(), Map.of());
    }

    private static CatalogTool tool(String key, String capability) {
        return new CatalogTool(key, key, CatalogTool.Kind.SKILL, "https://example.org/" + key,
            "1.0", Set.of(capability), Set.of(), Set.of(), Set.of(key),
            List.of(new ToolSupport("support-" + key, "WINDOWS", "claude-code", "1.0",
                ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS)));
    }
}
