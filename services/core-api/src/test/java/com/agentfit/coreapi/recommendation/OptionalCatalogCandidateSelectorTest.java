package com.agentfit.coreapi.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptionalCatalogCandidateSelectorTest {
    private static final EnvironmentTarget TARGET =
        new EnvironmentTarget("WINDOWS", "claude-code", "2.1.270");

    @Test
    void offersOnlyExplicitOptionalCapabilityWithReviewedCompleteCombination() {
        var base = tool("base", "cap_document_reference");
        var optional = tool("browser", "cap_browser_verification");
        var unrequested = tool("database", "cap_database_schema_inspection");
        var release = new CatalogRelease("release-1", Map.of(
            base.key(), base, optional.key(), optional, unrequested.key(), unrequested),
            Set.of(new VerifiedCombination(Set.of("base", "browser"), TARGET)));

        assertEquals(List.of("browser"), OptionalCatalogCandidateSelector.select(release,
            TARGET, Map.of(), List.of("base"), Set.of("cap_browser_verification"),
            keys -> true));
    }

    @Test
    void rejectsStandaloneCandidateWhenBaseAndCandidateCombinationWasNotVerified() {
        var base = tool("base", "cap_document_reference");
        var optional = tool("browser", "cap_browser_verification");
        var release = new CatalogRelease("release-1", Map.of(base.key(), base,
            optional.key(), optional), Set.of());

        assertEquals(List.of(), OptionalCatalogCandidateSelector.select(release,
            TARGET, Map.of(), List.of("base"), Set.of("cap_browser_verification"),
            keys -> true));
    }

    private static CatalogTool tool(String key, String capability) {
        var support = new ToolSupport("support-" + key, "WINDOWS", "claude-code",
            "2.1.270", ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS);
        return new CatalogTool(key, key, CatalogTool.Kind.SKILL,
            "https://example.org/" + key, "1.0", Set.of(capability), Set.of(),
            Set.of(), Set.of("component-" + key), List.of(support));
    }
}
