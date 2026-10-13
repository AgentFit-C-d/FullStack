package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewCapabilityCoverageTest {
    private static final EnvironmentTarget TARGET = new EnvironmentTarget("WINDOWS", "claude-code", "1.0");
    private static final String DOCUMENT = "cap_document_reference";
    private static final String BROWSER = "cap_browser_verification";

    @Test
    void partialChoiceReportsOnlyUnmetRequiredCapability() {
        var coverage = PreviewCapabilityCoverage.evaluate(release(), TARGET,
            Set.of(DOCUMENT, BROWSER), List.of("docs"), Map.of());

        assertEquals(List.of(BROWSER), coverage.unmetCapabilityKeys());
        assertFalse(coverage.complete());
    }

    @Test
    void exactVerifiedInstalledToolCanCoverUnselectedRequirement() {
        var covered = PreviewCapabilityCoverage.evaluate(release(), TARGET,
            Set.of(DOCUMENT, BROWSER), List.of("docs"), Map.of("browser", "1.0"));
        assertTrue(covered.complete());
        assertEquals(List.of(), covered.unmetCapabilityKeys());

        var wrongVersion = PreviewCapabilityCoverage.evaluate(release(), TARGET,
            Set.of(DOCUMENT, BROWSER), List.of("docs"), Map.of("browser", "0.9"));
        assertEquals(List.of(BROWSER), wrongVersion.unmetCapabilityKeys());
    }

    @Test
    void unknownRequiredCapabilityOrSelectedToolIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> PreviewCapabilityCoverage.evaluate(release(), TARGET,
                Set.of("invented"), List.of("docs"), Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> PreviewCapabilityCoverage.evaluate(release(), TARGET,
                Set.of(DOCUMENT), List.of("invented"), Map.of()));
    }

    @Test
    void unreviewedInstalledCombinationCannotErasePartialWarning() {
        var unreviewed = new CatalogRelease("test", release().tools(), Set.of());
        assertThrows(IllegalArgumentException.class,
            () -> PreviewCapabilityCoverage.evaluate(unreviewed, TARGET,
                Set.of(DOCUMENT, BROWSER), List.of("docs"), Map.of("browser", "1.0")));
    }

    @Test
    void installedToolWithMissingDependencyCannotCountAsCoverage() {
        var browser = new CatalogTool("browser", "browser", CatalogTool.Kind.SKILL,
            "https://example.org/browser", "1.0", Set.of(BROWSER), Set.of("runtime"),
            Set.of(), Set.of("browser"), tool("browser", BROWSER).support());
        var dependency = tool("runtime", DOCUMENT);
        var release = new CatalogRelease("test", Map.of("docs", tool("docs", DOCUMENT),
            "browser", browser, "runtime", dependency), Set.of());
        assertThrows(IllegalArgumentException.class,
            () -> PreviewCapabilityCoverage.evaluate(release, TARGET,
                Set.of(DOCUMENT, BROWSER), List.of("docs"), Map.of("browser", "1.0")));
    }

    private static CatalogRelease release() {
        return new CatalogRelease("test", Map.of(
            "docs", tool("docs", DOCUMENT), "browser", tool("browser", BROWSER)),
            Set.of(new VerifiedCombination(Set.of("docs", "browser"), TARGET)));
    }

    private static CatalogTool tool(String key, String capability) {
        return new CatalogTool(key, key, CatalogTool.Kind.SKILL, "https://example.org/" + key,
            "1.0", Set.of(capability), Set.of(), Set.of(), Set.of(key),
            List.of(new ToolSupport("support-" + key, "WINDOWS", "claude-code", "1.0",
                ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS)));
    }
}
