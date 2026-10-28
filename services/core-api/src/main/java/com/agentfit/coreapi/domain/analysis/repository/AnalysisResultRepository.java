package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisResult;
import java.util.Optional;
import java.util.Map;

public interface AnalysisResultRepository {
    default AnalysisResult save(AnalysisResult result) {
        return result;
    }

    default Optional<AnalysisResult> findByAttemptId(long attemptId) {
        return Optional.empty();
    }

    default Optional<AnalysisResult> findLatestByProjectId(String projectId) {
        return Optional.empty();
    }

    default void updateHumanReview(long attemptId, Map<String, Object> review) {
        // In-memory repositories used by unit tests may not need persistence.
    }
}
