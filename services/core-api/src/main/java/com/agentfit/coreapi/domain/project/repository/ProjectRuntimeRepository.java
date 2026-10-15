package com.agentfit.coreapi.domain.project.repository;

import com.agentfit.coreapi.domain.project.entity.ProjectRuntime;
import java.util.List;

public interface ProjectRuntimeRepository {
    default void replace(String projectId, List<ProjectRuntime> runtimes) { }
    default List<ProjectRuntime> findByProjectId(String projectId) { return List.of(); }
}
