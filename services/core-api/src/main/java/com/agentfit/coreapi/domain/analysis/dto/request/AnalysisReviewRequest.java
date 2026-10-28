package com.agentfit.coreapi.domain.analysis.dto.request;

import java.util.List;

public record AnalysisReviewRequest(
        List<String> confirmedCandidateIds,
        List<String> removedCandidateIds,
        List<ManualCandidateRequest> manualCandidates) {
    public List<String> confirmedCandidateIdsOrEmpty() {
        return confirmedCandidateIds == null ? List.of() : List.copyOf(confirmedCandidateIds);
    }

    public List<String> removedCandidateIdsOrEmpty() {
        return removedCandidateIds == null ? List.of() : List.copyOf(removedCandidateIds);
    }

    public List<ManualCandidateRequest> manualCandidatesOrEmpty() {
        return manualCandidates == null ? List.of() : List.copyOf(manualCandidates);
    }
}
