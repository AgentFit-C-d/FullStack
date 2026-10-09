package com.agentfit.coreapi.recommendation;

import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.CatalogValidator;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;

import static com.agentfit.coreapi.recommendation.RecommendationDecision.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecommendationEngineTest {
    private static final EnvironmentTarget TARGET = new EnvironmentTarget("WINDOWS", "example-client", "1.0");
    private static final String DOC = "cap_document_reference";
    private static final String TEST = "cap_test_execution";

    private CatalogTool tool(String key, Set<String> capabilities, Set<String> dependencies,
                             Set<String> conflicts, Set<String> components, ToolSupport.Check check) {
        return new CatalogTool(key, "1", capabilities, dependencies, conflicts, components,
            List.of(new ToolSupport(key + "-win", "WINDOWS", "example-client", "1.0",
                check, check, check)));
    }

    private RecommendationInput input(Set<String> required, Set<String> installed) {
        Map<String, String> versions = new HashMap<>();
        installed.forEach(key -> versions.put(key, "1"));
        return new RecommendationInput(required, false, false, TARGET, versions);
    }

    @Test
    void returnsAllFourOutcomesWithoutConfusingMissingInputAndNoCandidate() {
        CatalogTool doc = tool("doc", Set.of(DOC), Set.of(), Set.of(), Set.of("component-doc"), ToolSupport.Check.PASS);
        CatalogRelease release = new CatalogRelease("r1", Map.of("doc", doc), Set.of());
        RecommendationEngine engine = new RecommendationEngine(release);

        assertEquals(RECOMMENDED, engine.decide(input(Set.of(DOC), Set.of())).status());
        assertEquals(List.of("doc"), engine.decide(input(Set.of(DOC), Set.of())).toolKeys());
        assertEquals(NO_ADDITIONS_NEEDED, engine.decide(input(Set.of(DOC), Set.of("doc"))).status());
        assertEquals(List.of("installed_components_cover_required"),
            engine.decide(input(Set.of(DOC), Set.of("doc"))).reasonCodes());
        assertEquals(NEEDS_INFORMATION,
            engine.decide(new RecommendationInput(Set.of(DOC), false, false, null, Map.of())).status());
        assertEquals(NO_COMPATIBLE_TOOLS, engine.decide(input(Set.of(TEST), Set.of())).status());
        assertEquals(List.of("no_verified_capability_support"),
            engine.decide(input(Set.of(TEST), Set.of())).reasonCodes());
    }

    @Test
    void emptyRequiredCapabilitiesHaveDistinctNoAdditionReason() {
        RecommendationEngine engine = new RecommendationEngine(new CatalogRelease("r1", Map.of(), Set.of()));
        RecommendationDecision decision = engine.decide(input(Set.of(), Set.of()));
        assertEquals(NO_ADDITIONS_NEEDED, decision.status());
        assertEquals(List.of("no_required_capabilities"), decision.reasonCodes());
        assertEquals(List.of(), decision.questionCodes());
    }

    @Test
    void relevantPendingConflictNeverBecomesAConfirmedRequiredCapability() {
        RecommendationEngine engine = new RecommendationEngine(new CatalogRelease("r1", Map.of(), Set.of()));
        RecommendationDecision result = engine.decide(
            new RecommendationInput(Set.of(), false, true, TARGET, Map.of()));
        assertEquals(NEEDS_INFORMATION, result.status());
        assertEquals(List.of("pending_conflict"), result.questionCodes());
    }

    @Test
    void unverifiedStandaloneAndCombinationChecksFailClosed() {
        CatalogTool doc = tool("doc", Set.of(DOC), Set.of(), Set.of(), Set.of(), ToolSupport.Check.NOT_RUN);
        RecommendationEngine engine = new RecommendationEngine(
            new CatalogRelease("r1", Map.of("doc", doc), Set.of()));
        assertEquals(NO_COMPATIBLE_TOOLS, engine.decide(input(Set.of(DOC), Set.of())).status());

        CatalogTool verifiedDoc = tool("doc", Set.of(DOC), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        CatalogTool verifiedTest = tool("test", Set.of(TEST), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        engine = new RecommendationEngine(new CatalogRelease("r2",
            Map.of("doc", verifiedDoc, "test", verifiedTest), Set.of()));
        RecommendationDecision unsupportedCombination = engine.decide(input(Set.of(DOC, TEST), Set.of()));
        assertEquals(NO_COMPATIBLE_TOOLS, unsupportedCombination.status());
        assertEquals(List.of("no_verified_compatible_combination"), unsupportedCombination.reasonCodes());
        engine = new RecommendationEngine(new CatalogRelease("r3",
            Map.of("doc", verifiedDoc, "test", verifiedTest),
            Set.of(new VerifiedCombination(Set.of("doc", "test"), TARGET))));
        assertEquals(RECOMMENDED, engine.decide(input(Set.of(DOC, TEST), Set.of())).status());
        assertEquals(NO_COMPATIBLE_TOOLS, engine.decide(new RecommendationInput(Set.of(DOC, TEST),
            false, false, new EnvironmentTarget("WINDOWS", "example-client", "2.0"), Map.of())).status());
    }

    @Test
    void dependenciesConflictsAndIncludedComponentsAreCheckedAcrossSelection() {
        CatalogTool doc = tool("doc", Set.of(DOC), Set.of("runtime"), Set.of(), Set.of("shared"), ToolSupport.Check.PASS);
        CatalogTool runtime = tool("runtime", Set.of(), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        CatalogRelease valid = new CatalogRelease("r1", Map.of("doc", doc, "runtime", runtime),
            Set.of(new VerifiedCombination(Set.of("doc", "runtime"), TARGET)));
        assertEquals(List.of("doc", "runtime"),
            new RecommendationEngine(valid).decide(input(Set.of(DOC), Set.of())).toolKeys());

        CatalogTool conflicting = tool("runtime", Set.of(), Set.of(), Set.of("doc"), Set.of(), ToolSupport.Check.PASS);
        assertThrows(CatalogValidator.InvalidCatalogException.class,
            () -> new RecommendationEngine(new CatalogRelease("r2",
                Map.of("doc", doc, "runtime", conflicting),
                Set.of(new VerifiedCombination(Set.of("doc", "runtime"), TARGET)))));

        CatalogTool overlap = tool("runtime", Set.of(), Set.of(), Set.of(), Set.of("shared"), ToolSupport.Check.PASS);
        assertThrows(CatalogValidator.InvalidCatalogException.class,
            () -> new RecommendationEngine(new CatalogRelease("r3",
                Map.of("doc", doc, "runtime", overlap),
                Set.of(new VerifiedCombination(Set.of("doc", "runtime"), TARGET)))));
    }

    @Test
    void malformedCatalogAndUnknownCapabilityAreNotNormalNoCandidateResults() {
        CatalogTool cyclic = tool("a", Set.of(DOC), Set.of("b"), Set.of(), Set.of(), ToolSupport.Check.PASS);
        CatalogTool cyclicPeer = tool("b", Set.of(), Set.of("a"), Set.of(), Set.of(), ToolSupport.Check.PASS);
        assertThrows(CatalogValidator.InvalidCatalogException.class,
            () -> new RecommendationEngine(new CatalogRelease("r1", Map.of("a", cyclic, "b", cyclicPeer), Set.of())));
        CatalogTool unknown = tool("x", Set.of("invented_capability"), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        assertThrows(CatalogValidator.InvalidCatalogException.class,
            () -> new RecommendationEngine(new CatalogRelease("r2", Map.of("x", unknown), Set.of())));
        RecommendationEngine engine = new RecommendationEngine(new CatalogRelease("r3", Map.of(), Set.of()));
        assertThrows(IllegalArgumentException.class,
            () -> engine.decide(input(Set.of("invented_capability"), Set.of())));
    }

    @Test
    void rejectsSupportEvidenceKeyReusedByDifferentTools() {
        ToolSupport shared = new ToolSupport("shared-support", "WINDOWS", "example-client", "1.0",
            ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS);
        CatalogTool doc = new CatalogTool("doc", "1", Set.of(DOC), Set.of(), Set.of(), Set.of(),
            List.of(shared));
        CatalogTool test = new CatalogTool("test", "1", Set.of(TEST), Set.of(), Set.of(), Set.of(),
            List.of(shared));

        assertThrows(CatalogValidator.InvalidCatalogException.class,
            () -> new RecommendationEngine(new CatalogRelease("r1",
                Map.of("doc", doc, "test", test), Set.of())));
    }

    @Test
    void rejectsToolThatBothRequiresAndConflictsWithTheSameDependency() {
        CatalogTool doc = tool("doc", Set.of(DOC), Set.of("runtime"), Set.of("runtime"),
            Set.of(), ToolSupport.Check.PASS);
        CatalogTool runtime = tool("runtime", Set.of(), Set.of(), Set.of(), Set.of(),
            ToolSupport.Check.PASS);

        assertThrows(CatalogValidator.InvalidCatalogException.class,
            () -> new RecommendationEngine(new CatalogRelease("r1",
                Map.of("doc", doc, "runtime", runtime), Set.of())));

        CatalogTool reverseConflict = tool("runtime", Set.of(), Set.of(), Set.of("doc"),
            Set.of(), ToolSupport.Check.PASS);
        CatalogTool dependent = tool("doc", Set.of(DOC), Set.of("runtime"), Set.of(),
            Set.of(), ToolSupport.Check.PASS);
        assertThrows(CatalogValidator.InvalidCatalogException.class,
            () -> new RecommendationEngine(new CatalogRelease("r2",
                Map.of("doc", dependent, "runtime", reverseConflict), Set.of())));
    }

    @Test
    void selectsFewestToolsThenStableKeyOrderAndDoesNotGuessOtherVersions() {
        CatalogTool alpha = tool("alpha", Set.of(DOC), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        CatalogTool beta = tool("beta", Set.of(DOC), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        RecommendationEngine engine = new RecommendationEngine(new CatalogRelease("r1",
            Map.of("beta", beta, "alpha", alpha),
            Set.of(new VerifiedCombination(Set.of("alpha", "beta"), TARGET))));
        assertEquals(List.of("alpha"), engine.decide(input(Set.of(DOC), Set.of())).toolKeys());
        assertEquals(NO_COMPATIBLE_TOOLS, engine.decide(new RecommendationInput(Set.of(DOC),
            false, false, new EnvironmentTarget("WINDOWS", "example-client", "2.0"), Map.of())).status());
    }

    @Test
    void installedCatalogHintWithDifferentVersionDoesNotCountAsExistingCapability() {
        CatalogTool doc = tool("doc", Set.of(DOC), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        RecommendationEngine engine = new RecommendationEngine(new CatalogRelease("r1", Map.of("doc", doc), Set.of()));
        RecommendationDecision decision = engine.decide(new RecommendationInput(Set.of(DOC),
            false, false, TARGET, Map.of("doc", "0")));
        assertEquals(RECOMMENDED, decision.status());
        assertEquals(List.of("doc"), decision.toolKeys());
    }

    @Test
    void unknownRelevantInstalledVersionAsksBeforeClaimingNoAdditionsNeeded() {
        CatalogTool doc = tool("doc", Set.of(DOC), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        RecommendationEngine engine = new RecommendationEngine(new CatalogRelease("r1", Map.of("doc", doc), Set.of()));
        Map<String, String> installed = new HashMap<>();
        installed.put("doc", null);
        RecommendationDecision decision = engine.decide(new RecommendationInput(Set.of(DOC),
            false, false, TARGET, installed));
        assertEquals(NEEDS_INFORMATION, decision.status());
        assertEquals(List.of("installed_component_version"), decision.questionCodes());
    }

    @Test
    void rejectsMalformedInstalledComponentEntriesInsteadOfTreatingThemAsAbsent() {
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "1");
        assertThrows(IllegalArgumentException.class,
            () -> new RecommendationInput(Set.of(DOC), false, false, TARGET, nullKey));
        assertThrows(IllegalArgumentException.class,
            () -> new RecommendationInput(Set.of(DOC), false, false, TARGET, Map.of(" ", "1")));
        assertThrows(IllegalArgumentException.class,
            () -> new RecommendationInput(Set.of(DOC), false, false, TARGET, Map.of("doc", " ")));
    }

    @Test
    void combinationIsNotSharedAcrossOtherwiseSupportedClientVersions() {
        CatalogTool doc = tool("doc", Set.of(DOC), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        CatalogTool test = tool("test", Set.of(TEST), Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS);
        doc = new CatalogTool(doc.key(), doc.version(), doc.capabilityKeys(), doc.dependencyKeys(),
            doc.conflictKeys(), doc.includedComponentKeys(), List.of(doc.support().getFirst(),
                new ToolSupport("doc-win-v2", "WINDOWS", "example-client", "2.0",
                    ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS)));
        test = new CatalogTool(test.key(), test.version(), test.capabilityKeys(), test.dependencyKeys(),
            test.conflictKeys(), test.includedComponentKeys(), List.of(test.support().getFirst(),
                new ToolSupport("test-win-v2", "WINDOWS", "example-client", "2.0",
                    ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS)));
        RecommendationEngine engine = new RecommendationEngine(new CatalogRelease("r1",
            Map.of("doc", doc, "test", test),
            Set.of(new VerifiedCombination(Set.of("doc", "test"), TARGET))));
        assertEquals(RECOMMENDED, engine.decide(input(Set.of(DOC, TEST), Set.of())).status());
        assertEquals(NO_COMPATIBLE_TOOLS, engine.decide(new RecommendationInput(Set.of(DOC, TEST),
            false, false, new EnvironmentTarget("WINDOWS", "example-client", "2.0"), Map.of())).status());
    }

    @Test
    void findsSingleToolAnswerBeforeRejectingLargeCombinationSearch() {
        Map<String, CatalogTool> tools = new HashMap<>();
        IntStream.range(0, 20).forEach(index -> {
            String key = "unrelated-" + index;
            tools.put(key, tool(key, Set.of(TEST), Set.of(), Set.of(), Set.of(),
                ToolSupport.Check.PASS));
        });
        tools.put("doc", tool("doc", Set.of(DOC), Set.of(), Set.of(), Set.of(),
            ToolSupport.Check.PASS));
        RecommendationDecision result = new RecommendationEngine(
            new CatalogRelease("r1", tools, Set.of())).decide(input(Set.of(DOC), Set.of()));

        assertEquals(RECOMMENDED, result.status());
        assertEquals(List.of("doc"), result.toolKeys());
        assertThrows(CatalogValidator.InvalidCatalogException.class,
            () -> new RecommendationEngine(new CatalogRelease("r1", tools, Set.of()))
                .decide(input(Set.of(DOC, TEST), Set.of())));
    }
}
