package com.agentfit.coreapi.catalog.discovery;

import java.nio.file.Path;
import java.util.List;

/** On-demand discovery workflow. Only an explicit Catalog approval can activate a candidate. */
public final class SkillsShCandidateRefresh {
    private final SkillsShCandidateCollector collector;

    public SkillsShCandidateRefresh(SkillsShCandidateCollector collector) {
        if (collector == null) throw new IllegalArgumentException("collector required");
        this.collector = collector;
    }

    public List<SkillCandidate> refresh(String query, int limit, Path snapshot) {
        List<SkillCandidate> discovered = collector.search(query, limit);
        return SkillsShCandidateSnapshot.refresh(snapshot, discovered);
    }
}
