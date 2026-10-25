package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class SkillsShRealCatalogWorkflowTest {
    private static final Path CATALOG = Path.of("..", "..", "catalog-candidates",
        "obra-tdd-windows-claude-2.1.270-verified").toAbsolutePath().normalize();

    @Test
    void verifiedSkillsShSkillCanBeRecommendedAndRenderedForClaudeCode() {
        String source = "profile.item.features.0";
        var claims = CapabilityKey.keys().stream().map(key -> new AiCapabilityIntake.Claim(
            key, key.equals("cap_test_authoring")
                ? AiCapabilityIntake.Need.REQUIRED : AiCapabilityIntake.Need.NOT_REQUESTED,
            "confirmed project feature", key.equals("cap_test_authoring")
                ? List.of(source) : List.of())).toList();
        var bundle = CatalogBundleLoader.load(CATALOG);
        var result = CatalogRecommendationWorkflow.evaluate(CATALOG, bundle.catalogHash(),
            new CatalogRecommendationWorkflow.Request(claims, List.of(), Set.of(source), false,
                new EnvironmentTarget("WINDOWS", "claude-code", "2.1.270"), Map.of()));

        assertEquals(RecommendationDecision.Status.RECOMMENDED, result.decision().status());
        assertEquals(List.of("obra-superpowers-test-driven-development"),
            result.decision().toolKeys());
        var files = CatalogStaticTemplateRenderer.render(CATALOG, bundle.catalogHash(),
            Set.of("obra-superpowers-test-driven-development"));
        assertTrue(files.stream().anyMatch(file -> file.relativePath().equals(
            ".claude/skills/test-driven-development/SKILL.md")
            && file.content().startsWith("---")));
        assertTrue(files.stream().anyMatch(file -> file.relativePath().equals(
            ".claude/skills/test-driven-development/writing-good-tests.md")));
    }

    @Test
    void unverifiedCandidateCannotBeRecommended() {
        Path staged = Path.of("..", "..", "catalog-candidates",
            "obra-tdd-windows-claude-2.1.270").toAbsolutePath().normalize();
        String source = "profile.item.features.0";
        var claims = CapabilityKey.keys().stream().map(key -> new AiCapabilityIntake.Claim(
            key, key.equals("cap_test_authoring")
                ? AiCapabilityIntake.Need.REQUIRED : AiCapabilityIntake.Need.NOT_REQUESTED,
            "confirmed project feature", key.equals("cap_test_authoring")
                ? List.of(source) : List.of())).toList();
        var bundle = CatalogBundleLoader.load(staged);
        var result = CatalogRecommendationWorkflow.evaluate(staged, bundle.catalogHash(),
            new CatalogRecommendationWorkflow.Request(claims, List.of(), Set.of(source), false,
                new EnvironmentTarget("WINDOWS", "claude-code", "2.1.270"), Map.of()));

        assertEquals(RecommendationDecision.Status.NO_COMPATIBLE_TOOLS,
            result.decision().status());
    }

    @Test
    void catalogReadShowsOnlyExactVerifiedTarget() {
        String hash = CatalogBundleLoader.load(CATALOG).catalogHash();
        var matched = CatalogReadService.list(CATALOG, hash,
            new CatalogReadService.Filter("WINDOWS", "claude-code", "2.1.270",
                "cap_test_authoring"));
        assertEquals(1, matched.items().size());
        assertEquals("obra-superpowers-test-driven-development", matched.items().getFirst().key());
        assertTrue(matched.items().getFirst().support().getFirst().verified());

        var wrongVersion = CatalogReadService.list(CATALOG, hash,
            new CatalogReadService.Filter("WINDOWS", "claude-code", "2.1.271",
                "cap_test_authoring"));
        assertEquals(List.of(), wrongVersion.items());
    }

    @Test
    void verifiedSkillFlowsThroughPermissionPreviewApprovalAndZip() throws Exception {
        var bundle = CatalogBundleLoader.load(CATALOG);
        String hash = bundle.catalogHash();
        String release = bundle.releaseId();
        String tool = "obra-superpowers-test-driven-development";
        var target = new EnvironmentTarget("WINDOWS", "claude-code", "2.1.270");
        var basis = new PreviewBasis("project-1", 1, "profile-1", 1, "event-1",
            0, 0, 1, release, hash);
        var stored = new StoredRecommendationState("rec-1", basis,
            RecommendationDecision.Status.RECOMMENDED, List.of(tool));
        var request = new CatalogPreviewRequest(basis, "rec-1", target, List.of(tool),
            List.of(new PermissionSelection(tool, "edit-project-files", PermissionPolicy.ASK_EACH_TIME),
                new PermissionSelection(tool, "run-project-tests", PermissionPolicy.ASK_EACH_TIME)),
            ExistingState.UNKNOWN, List.of(), "static-v1");
        var result = CatalogPreviewAssembler.assemble(CATALOG, hash, stored, basis, target, request);
        var clock = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);
        var preview = new StoredPreviewState("preview-1", basis, result.fingerprint(),
            Instant.now(clock).minusSeconds(60), Instant.now(clock).plusSeconds(600));
        StoredApprovalState approval = CatalogReadyPreviewApprovalWorkflow.issue(CATALOG, hash,
            preview, basis, "preview-1", result.fingerprint(), true, "approval-1", clock);
        var generated = CatalogApprovedConfigurationWorkflow.generate(CATALOG, hash,
            stored, basis, target, request, preview, approval,
            "approval-1", "preview-1", result.fingerprint(), "generation-1", clock);
        Set<String> paths = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(generated.zipBytes()),
            StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                paths.add(entry.getName());
                if (entry.getName().endsWith("/SKILL.md")) {
                    assertTrue(new String(zip.readAllBytes(), StandardCharsets.UTF_8)
                        .contains("name: test-driven-development"));
                }
            }
        }
        assertTrue(paths.contains(".claude/skills/test-driven-development/SKILL.md"));
        assertTrue(paths.contains(".claude/skills/test-driven-development/writing-good-tests.md"));
        assertTrue(paths.contains("SUPERPOWERS-LICENSE.txt"));
    }
}
