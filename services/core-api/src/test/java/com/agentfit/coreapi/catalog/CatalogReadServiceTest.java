package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.recommendation.selection.PermissionMapping;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import java.time.LocalDate;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CatalogReadServiceTest {
    private static final String HASH = "a".repeat(64);
    @TempDir Path directory;

    @Test
    void loadsOnlyHashApprovedBundleFromDisk() throws Exception {
        String approved = SyntheticCatalogBundle.write(directory);
        var snapshot = CatalogReadService.list(directory, approved,
            new CatalogReadService.Filter("WINDOWS", "example-client", "cap_document_reference"));
        assertEquals(1, snapshot.items().size());
        assertEquals("example-tool", snapshot.items().getFirst().key());
        assertTrue(snapshot.items().getFirst().support().getFirst().verified());
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogReadService.list(directory, "b".repeat(64),
                new CatalogReadService.Filter(null, null, null)));
    }

    @Test
    void filtersApprovedCatalogAndPreservesExactSupportChecks() {
        var snapshot = CatalogReadService.list(parsed(), HASH,
            new CatalogReadService.Filter("WINDOWS", "claude-code", "cap_document_reference"));
        assertEquals("reviewed-1", snapshot.releaseId());
        assertEquals(HASH, snapshot.catalogHash());
        assertEquals(1, snapshot.items().size());
        var item = snapshot.items().getFirst();
        assertEquals("doc-skill", item.key());
        assertEquals(2, item.support().size());
        assertTrue(item.support().getFirst().verified());
        assertFalse(item.support().get(1).verified());
        assertEquals("2.0", item.support().get(1).clientVersion());
        assertEquals("https://example.org/support-1", item.support().getFirst().evidence().sourceUrl());
        assertEquals(1, item.permissions().size());
        assertEquals(PermissionPolicy.ASK_EACH_TIME,
            item.permissions().getFirst().mapping().supportedPolicies().iterator().next());
    }

    @Test
    void refusesUnapprovedHashAndUnknownCapabilityFilter() {
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogReadService.list(parsed(), "b".repeat(64),
                new CatalogReadService.Filter(null, null, null)));
        assertThrows(IllegalArgumentException.class,
            () -> CatalogReadService.list(parsed(), HASH,
                new CatalogReadService.Filter(null, null, "invented")));
    }

    private ParsedCatalog parsed() {
        var first = new ToolSupport("support-1", "WINDOWS", "claude-code", "1.0",
            ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS);
        var second = new ToolSupport("support-2", "WINDOWS", "claude-code", "2.0",
            ToolSupport.Check.PASS, ToolSupport.Check.NOT_RUN, ToolSupport.Check.NOT_RUN);
        var tool = new CatalogTool("doc-skill", "1.0", Set.of("cap_document_reference"),
            Set.of(), Set.of(), Set.of("doc-component"), List.of(first, second));
        var other = new CatalogTool("test-skill", "1.0", Set.of("cap_test_execution"),
            Set.of(), Set.of(), Set.of("test-component"), List.of());
        var release = new CatalogRelease("reviewed-1", Map.of(tool.key(), tool, other.key(), other), Set.of());
        var permission = new PermissionMapping("doc-skill", "read", false,
            Set.of(PermissionPolicy.ASK_EACH_TIME));
        return new ParsedCatalog(HASH, release, List.of(permission), Map.of(
            "support:support-1", new VerificationEvidence("https://example.org/support-1", LocalDate.parse("2026-10-09")),
            "support:support-2", new VerificationEvidence("https://example.org/support-2", LocalDate.parse("2026-10-09")),
            "permission:doc-skill:read", new VerificationEvidence("https://example.org/permission", LocalDate.parse("2026-10-09"))));
    }
}
