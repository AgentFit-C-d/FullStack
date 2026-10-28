package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.dto.request.AnalysisReviewRequest;
import com.agentfit.coreapi.domain.analysis.dto.request.ManualCandidateRequest;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisResult;
import com.agentfit.coreapi.domain.analysis.exception.AnalysisResultNotFoundException;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisResultRepository;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.domain.project.repository.ProjectRepository;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisReviewService {
    private final ProjectRepository projects;
    private final AnalysisResultRepository results;
    private final Clock clock;

    public AnalysisReviewService(ProjectRepository projects, AnalysisResultRepository results, Clock clock) {
        this.projects = projects;
        this.results = results;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> save(
            AuthenticatedPrincipal principal,
            String projectId,
            AnalysisReviewRequest request) {
        if (principal == null) {
            throw new UnauthenticatedException();
        }
        projects.findByIdAndOwnerId(projectId, principal.userId()).orElseThrow(ProjectNotFoundException::new);
        AnalysisResult result = results.findLatestByProjectId(projectId).orElseThrow(AnalysisResultNotFoundException::new);
        List<String> confirmed = request.confirmedCandidateIdsOrEmpty();
        List<String> removed = request.removedCandidateIdsOrEmpty();
        if (!Collections.disjoint(new HashSet<>(confirmed), new HashSet<>(removed))) {
            throw new IllegalArgumentException("A candidate cannot be confirmed and removed at the same time");
        }
        HashSet<String> candidateIds = new HashSet<>();
        Object candidates = result.result().get("candidates");
        if (candidates instanceof List<?> values) {
            for (Object candidate : values) {
                if (candidate instanceof Map<?, ?> value && value.get("id") != null) {
                    candidateIds.add(String.valueOf(value.get("id")));
                }
            }
        }
        if (!candidateIds.containsAll(confirmed) || !candidateIds.containsAll(removed)) {
            throw new IllegalArgumentException("Unknown analysis candidate");
        }

        List<Map<String, Object>> manualCandidates = new ArrayList<>();
        for (ManualCandidateRequest manual : request.manualCandidatesOrEmpty()) {
            if (manual == null || manual.field() == null || manual.field().isBlank()
                    || manual.value() == null || manual.value().isBlank()) {
                throw new IllegalArgumentException("Manual candidate field and value are required");
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", "manual-" + UUID.randomUUID());
            value.put("field", manual.field().trim());
            value.put("proposedValue", manual.value().trim());
            value.put("status", "confirmed");
            value.put("source", "USER_INPUT");
            value.put("evidence", List.of());
            manualCandidates.add(value);
        }

        Map<String, Object> review = new LinkedHashMap<>();
        review.put("confirmedCandidateIds", List.copyOf(confirmed));
        review.put("removedCandidateIds", List.copyOf(removed));
        review.put("manualCandidates", manualCandidates);
        review.put("updatedAt", Instant.now(clock).toString());
        results.updateHumanReview(result.attemptId(), review);
        return review;
    }
}
