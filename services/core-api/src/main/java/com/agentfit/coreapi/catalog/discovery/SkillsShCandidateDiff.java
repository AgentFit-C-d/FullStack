package com.agentfit.coreapi.catalog.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Identifies discoveries that need human review; absent search hits are not deletions. */
public final class SkillsShCandidateDiff {
    private SkillsShCandidateDiff() {}

    public static List<SkillCandidate> needingReview(List<SkillCandidate> previous,
                                                      List<SkillCandidate> discovered) {
        Map<String, SkillCandidate> byId = new HashMap<>();
        for (SkillCandidate candidate : previous) {
            if (byId.putIfAbsent(candidate.externalId(), candidate) != null) {
                throw new IllegalArgumentException("duplicate previous candidate");
            }
        }
        List<SkillCandidate> changed = new ArrayList<>();
        for (SkillCandidate candidate : discovered) {
            SkillCandidate old = byId.get(candidate.externalId());
            if (old == null || !old.contentHash().equals(candidate.contentHash())
                || !old.name().equals(candidate.name())
                || !old.sourceUrl().equals(candidate.sourceUrl())) {
                changed.add(candidate);
            }
        }
        return List.copyOf(changed);
    }
}
