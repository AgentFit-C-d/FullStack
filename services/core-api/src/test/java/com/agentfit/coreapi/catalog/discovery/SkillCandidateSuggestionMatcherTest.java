package com.agentfit.coreapi.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillCandidateSuggestionMatcherTest {
    @Test
    void relatesDistinctCandidatesToNeededCapabilitiesWithoutClaimingCompatibility() {
        var result = SkillCandidateSuggestionMatcher.suggest(List.of(
            candidate("owner/repo/test-driven-development"),
            candidate("dotnet/skills/run-tests"),
            candidate("another/repo/agent-browser")),
            Set.of("cap_test_authoring", "cap_test_execution"),
            Set.of("cap_browser_verification"));

        assertEquals(List.of("dotnet/skills/run-tests", "owner/repo/test-driven-development",
            "another/repo/agent-browser"),
            result.items().stream().map(SkillCandidateSuggestionMatcher.Item::externalId).toList());
        assertEquals(Set.of(), result.unmatchedRequiredCapabilityKeys());
        assertEquals(Set.of("cap_test_execution"), result.items().getFirst().relatedCapabilityKeys());
        assertEquals("NAME_RULE", result.items().getFirst().evidence());
        assertEquals(List.of("dotnet/skills/run-tests", "owner/repo/test-driven-development"),
            result.proposedCombinationExternalIds());
        assertEquals("UNCONFIRMED", result.combinationStatus());
    }

    @Test
    void leavesUnknownCoverageVisibleAndRejectsUnknownCapabilityKeys() {
        var result = SkillCandidateSuggestionMatcher.suggest(List.of(candidate("owner/repo/run-tests")),
            Set.of("cap_test_execution", "cap_database_schema_inspection"), Set.of());
        assertEquals(Set.of("cap_database_schema_inspection"), result.unmatchedRequiredCapabilityKeys());
        assertThrows(IllegalArgumentException.class, () -> SkillCandidateSuggestionMatcher.suggest(
            List.of(), Set.of("made-up"), Set.of()));
    }

    private static SkillCandidate candidate(String id) {
        String source = id.substring(0, id.lastIndexOf('/'));
        return new SkillCandidate(id, id.substring(id.lastIndexOf('/') + 1), source,
            "https://skills.sh/" + id, "a".repeat(64), Instant.parse("2026-10-28T00:00:00Z"));
    }
}
