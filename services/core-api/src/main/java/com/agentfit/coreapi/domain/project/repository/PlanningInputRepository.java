package com.agentfit.coreapi.domain.project.repository;

import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import java.util.Optional;

public interface PlanningInputRepository {
    default void save(PlanningInput input) { }
    default Optional<PlanningInput> findCurrent(String projectId) { return Optional.empty(); }
    default Optional<PlanningInput> findByProjectIdAndRevision(String projectId, long revision) { return Optional.empty(); }
    default void scrub(String projectId, long revision) { }
    default void deleteByProjectId(String projectId) { }
}
