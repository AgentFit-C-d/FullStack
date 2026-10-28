package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.selection.PermissionMapping;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import java.time.LocalDate;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogRevisionImpactTest {
    private static final EnvironmentTarget TARGET =
        new EnvironmentTarget("WINDOWS", "claude-code", "2.1.270");
    private static final VerifiedCombination PAIR =
        new VerifiedCombination(Set.of("tool-a", "tool-b"), TARGET);
    private static final VerificationEvidence EVIDENCE =
        new VerificationEvidence("https://example.org/review", LocalDate.parse("2026-10-10"));

    @Test
    void realClaudeCodeVersionChangeRequiresToolReevaluation() {
        Path root = Path.of("..", "..", "catalog-candidates");
        var before = CatalogBundleLoader.load(root.resolve(
            "obra-tdd-windows-claude-2.1.270-verified"));
        var after = CatalogBundleLoader.load(root.resolve(
            "obra-tdd-windows-claude-2.1.310-risk-reviewed"));

        var impact = CatalogRevisionImpact.compare(before, after);

        assertTrue(impact.revisionChanged());
        assertTrue(impact.toolsNeedingReview().contains(
            "obra-superpowers-test-driven-development"));
    }

    @Test
    void toolVersionChangeInvalidatesItsSupportAndContainingCombination() {
        var before = catalog("old", "1.0", EVIDENCE);
        var after = catalog("new", "2.0", EVIDENCE);

        var impact = CatalogRevisionImpact.compare(before, after, files(), files());

        assertEquals(Set.of("tool-a"), impact.toolsNeedingReview());
        assertEquals(Set.of(PAIR), impact.combinationsNeedingReview());
        assertTrue(impact.revisionChanged());
    }

    @Test
    void changedPermissionEvidenceInvalidatesToolAndCombination() {
        var before = catalog("old", "1.0", EVIDENCE);
        var reviewedLater = new VerificationEvidence("https://example.org/new-review",
            LocalDate.parse("2026-10-11"));
        var after = catalog("new", "1.0", reviewedLater);

        var impact = CatalogRevisionImpact.compare(before, after, files(), files());

        assertEquals(Set.of("tool-a"), impact.toolsNeedingReview());
        assertEquals(Set.of(PAIR), impact.combinationsNeedingReview());
    }

    @Test
    void changedRiskReviewInvalidatesToolAndContainingCombination() {
        var before = catalog("old", "1.0", EVIDENCE);
        var reviewed = new ToolRiskReview(ToolRiskReview.Status.REVIEWED,
            ToolRiskReview.Level.HIGH, ToolRiskReview.ExternalAuth.REQUIRED,
            "External account and write access",
            new VerificationEvidence("https://example.org/risk", LocalDate.of(2026, 10, 26)));
        var after = new ParsedCatalog("new", before.release(), before.permissionMappings(),
            before.evidence(), Map.of("tool-a", reviewed));

        var impact = CatalogRevisionImpact.compare(before, after, files(), files());

        assertEquals(Set.of("tool-a"), impact.toolsNeedingReview());
        assertEquals(Set.of(PAIR), impact.combinationsNeedingReview());
    }

    @Test
    void changedStaticOutputConservativelyInvalidatesEveryToolAndCombination() {
        var before = catalog("old", "1.0", EVIDENCE);
        var after = catalog("new", "1.0", EVIDENCE);
        var changed = Map.of("tools.json", "same", "guides/tool-a.md", "new guide");

        var impact = CatalogRevisionImpact.compare(before, after, files(), changed);

        assertEquals(Set.of("tool-a", "tool-b"), impact.toolsNeedingReview());
        assertEquals(Set.of(PAIR), impact.combinationsNeedingReview());
    }

    @Test
    void changedSupportEvidenceInvalidatesOnlyItsToolAndContainingCombination() {
        var before = catalog("old", "1.0", EVIDENCE);
        var changedEvidence = new java.util.HashMap<>(before.evidence());
        changedEvidence.put("support:support-a", new VerificationEvidence(
            "https://example.org/new-support-check", LocalDate.parse("2026-10-11")));
        var after = new ParsedCatalog("new", before.release(),
            before.permissionMappings(), changedEvidence);

        var impact = CatalogRevisionImpact.compare(before, after, files(), files());

        assertEquals(Set.of("tool-a"), impact.toolsNeedingReview());
        assertEquals(Set.of(PAIR), impact.combinationsNeedingReview());
    }

    @Test
    void changedCombinationEvidenceRequiresThatCombinationToBeReviewedAgain() {
        var before = catalog("old", "1.0", EVIDENCE);
        var changedEvidence = new java.util.HashMap<>(before.evidence());
        changedEvidence.put("combination:WINDOWS:claude-code:2.1.270:tool-a,tool-b",
            new VerificationEvidence("https://example.org/new-pair-check",
                LocalDate.parse("2026-10-11")));
        var after = new ParsedCatalog("new", before.release(),
            before.permissionMappings(), changedEvidence);

        var impact = CatalogRevisionImpact.compare(before, after, files(), files());

        assertTrue(impact.toolsNeedingReview().isEmpty());
        assertEquals(Set.of(PAIR), impact.combinationsNeedingReview());
    }

    @Test
    void newReleaseIdWithSameFilesDoesNotClaimNewClientVerificationIsRequired() {
        var impact = CatalogRevisionImpact.compare(catalog("old", "1.0", EVIDENCE),
            catalog("new", "1.0", EVIDENCE), files(), files());

        assertTrue(impact.revisionChanged());
        assertTrue(impact.toolsNeedingReview().isEmpty());
        assertTrue(impact.combinationsNeedingReview().isEmpty());
    }

    private static ParsedCatalog catalog(String revision, String toolVersion,
                                         VerificationEvidence permissionEvidence) {
        var supportA = new ToolSupport("support-a", "WINDOWS", "claude-code", "2.1.270",
            ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS);
        var supportB = new ToolSupport("support-b", "WINDOWS", "claude-code", "2.1.270",
            ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS);
        var toolA = new CatalogTool("tool-a", "Tool A", CatalogTool.Kind.MCP,
            "https://example.org/a", toolVersion, Set.of("cap_browser_verification"),
            Set.of(), Set.of(), Set.of("component-a"), List.of(supportA));
        var toolB = new CatalogTool("tool-b", "Tool B", CatalogTool.Kind.SKILL,
            "https://example.org/b", "1.0", Set.of("cap_document_reference"),
            Set.of(), Set.of(), Set.of("component-b"), List.of(supportB));
        var release = new CatalogRelease(revision, Map.of("tool-a", toolA, "tool-b", toolB),
            Set.of(PAIR));
        var mapping = new PermissionMapping("tool-a", "browser-read", false,
            Set.of(PermissionPolicy.ASK_EACH_TIME));
        return new ParsedCatalog(revision, release, List.of(mapping), Map.of(
            "support:support-a", EVIDENCE, "support:support-b", EVIDENCE,
            "permission:tool-a:browser-read", permissionEvidence,
            "combination:WINDOWS:claude-code:2.1.270:tool-a,tool-b", EVIDENCE));
    }

    private static Map<String, String> files() {
        return Map.of("tools.json", "same");
    }
}
