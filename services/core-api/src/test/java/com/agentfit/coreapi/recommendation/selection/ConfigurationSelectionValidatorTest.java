package com.agentfit.coreapi.recommendation.selection;

import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.catalog.model.CatalogRelease;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.CatalogValidator;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.catalog.model.VerifiedCombination;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigurationSelectionValidatorTest {
    private static final EnvironmentTarget TARGET = new EnvironmentTarget("WINDOWS", "example-client", "1.0");

    private CatalogTool tool(String key, Set<String> dependencies, Set<String> conflicts,
                             Set<String> components, ToolSupport.Check check) {
        return new CatalogTool(key, key, CatalogTool.Kind.SKILL, "https://example.org/" + key,
            "1", Set.of("cap_document_reference"), dependencies,
            conflicts, components, List.of(new ToolSupport(key + "-support", "WINDOWS", "example-client", "1.0",
                check, check, check)));
    }

    private ConfigurationSelectionValidator validator(CatalogRelease release) {
        return new ConfigurationSelectionValidator(release);
    }

    @Test
    void acceptsSingleSupportedToolAndItsExplicitReviewedPermission() {
        CatalogRelease release = new CatalogRelease("r1", Map.of("doc",
            tool("doc", Set.of(), Set.of(), Set.of("doc-component"), ToolSupport.Check.PASS)), Set.of());
        PermissionMapping mapping = new PermissionMapping("doc", "network", true,
            Set.of(PermissionPolicy.ASK_EACH_TIME, PermissionPolicy.DENY));
        PermissionSelection ask = new PermissionSelection("doc", "network", PermissionPolicy.ASK_EACH_TIME);
        assertEquals(List.of(ask), validator(release).validate(Set.of("doc"), TARGET,
            List.of(mapping), List.of(ask)));
        assertThrows(ConfigurationSelectionValidator.InvalidSelectionException.class,
            () -> validator(release).validate(Set.of("doc"), TARGET, List.of(mapping),
                List.of(new PermissionSelection("doc", "network", PermissionPolicy.DENY))));
    }

    @Test
    void blocksUnknownUnsupportedAndUnmatchedTarget() {
        CatalogRelease release = new CatalogRelease("r1", Map.of("doc",
            tool("doc", Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS)), Set.of());
        assertThrows(ConfigurationSelectionValidator.InvalidSelectionException.class,
            () -> validator(release).validate(Set.of("missing"), TARGET, List.of(), List.of()));
        assertThrows(ConfigurationSelectionValidator.InvalidSelectionException.class,
            () -> validator(release).validate(Set.of("doc"),
                new EnvironmentTarget("WINDOWS", "example-client", "2.0"), List.of(), List.of()));
        CatalogRelease unverified = new CatalogRelease("r2", Map.of("doc",
            tool("doc", Set.of(), Set.of(), Set.of(), ToolSupport.Check.NOT_RUN)), Set.of());
        assertThrows(ConfigurationSelectionValidator.InvalidSelectionException.class,
            () -> validator(unverified).validate(Set.of("doc"), TARGET, List.of(), List.of()));
    }

    @Test
    void blocksMissingDependencyConflictOverlapAndUnverifiedCombination() {
        CatalogTool doc = tool("doc", Set.of("runtime"), Set.of(), Set.of("shared"), ToolSupport.Check.PASS);
        CatalogTool runtime = tool("runtime", Set.of(), Set.of(), Set.of("runtime"), ToolSupport.Check.PASS);
        CatalogRelease release = new CatalogRelease("r1", Map.of("doc", doc, "runtime", runtime), Set.of());
        assertThrows(ConfigurationSelectionValidator.InvalidSelectionException.class,
            () -> validator(release).validate(Set.of("doc"), TARGET, List.of(), List.of()));
        assertThrows(ConfigurationSelectionValidator.InvalidSelectionException.class,
            () -> validator(release).validate(Set.of("doc", "runtime"), TARGET, List.of(), List.of()));

        CatalogRelease verified = new CatalogRelease("r2", Map.of("doc", doc, "runtime", runtime),
            Set.of(new VerifiedCombination(Set.of("doc", "runtime"), TARGET)));
        assertEquals(List.of(), validator(verified).validate(Set.of("doc", "runtime"),
            TARGET, List.of(), List.of()));
        CatalogTool conflict = tool("runtime", Set.of(), Set.of("doc"), Set.of(), ToolSupport.Check.PASS);
        CatalogRelease conflicting = new CatalogRelease("r3", Map.of("doc", doc, "runtime", conflict),
            Set.of(new VerifiedCombination(Set.of("doc", "runtime"), TARGET)));
        assertThrows(CatalogValidator.InvalidCatalogException.class, () -> validator(conflicting));
        CatalogTool overlap = tool("runtime", Set.of(), Set.of(), Set.of("shared"), ToolSupport.Check.PASS);
        CatalogRelease overlapping = new CatalogRelease("r4", Map.of("doc", doc, "runtime", overlap),
            Set.of(new VerifiedCombination(Set.of("doc", "runtime"), TARGET)));
        assertThrows(CatalogValidator.InvalidCatalogException.class, () -> validator(overlapping));
    }

    @Test
    void exactCombinationTargetIsRequiredEvenWhenEachToolSupportsAnotherVersion() {
        CatalogTool doc = dualVersionTool("doc");
        CatalogTool test = dualVersionTool("test");
        CatalogRelease release = new CatalogRelease("r1", Map.of("doc", doc, "test", test),
            Set.of(new VerifiedCombination(Set.of("doc", "test"), TARGET)));
        assertEquals(List.of(), validator(release).validate(Set.of("doc", "test"), TARGET,
            List.of(), List.of()));
        assertThrows(ConfigurationSelectionValidator.InvalidSelectionException.class,
            () -> validator(release).validate(Set.of("doc", "test"),
                new EnvironmentTarget("WINDOWS", "example-client", "2.0"), List.of(), List.of()));
    }

    private CatalogTool dualVersionTool(String key) {
        return new CatalogTool(key, key, CatalogTool.Kind.SKILL, "https://example.org/" + key,
            "1", Set.of("cap_document_reference"), Set.of(), Set.of(), Set.of(),
            List.of(new ToolSupport(key + "-v1", "WINDOWS", "example-client", "1.0",
                    ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS),
                new ToolSupport(key + "-v2", "WINDOWS", "example-client", "2.0",
                    ToolSupport.Check.PASS, ToolSupport.Check.PASS, ToolSupport.Check.PASS)));
    }

    @Test
    void rejectsPoliciesForUnselectedToolsAndAllowsEmptySelectionOnlyWithoutPolicies() {
        CatalogRelease release = new CatalogRelease("r1", Map.of("doc",
            tool("doc", Set.of(), Set.of(), Set.of(), ToolSupport.Check.PASS)), Set.of());
        PermissionMapping mapping = new PermissionMapping("doc", "network", false,
            Set.of(PermissionPolicy.ASK_EACH_TIME, PermissionPolicy.DENY));
        assertEquals(List.of(), validator(release).validate(Set.of(), TARGET, List.of(mapping), List.of()));
        assertThrows(ConfigurationSelectionValidator.InvalidSelectionException.class,
            () -> validator(release).validate(Set.of(), TARGET, List.of(mapping),
                List.of(new PermissionSelection("doc", "network", PermissionPolicy.DENY))));
    }
}
