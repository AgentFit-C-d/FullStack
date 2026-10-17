package com.agentfit.coreapi.domain.project.repository;

import com.agentfit.coreapi.domain.project.entity.ProjectRuntime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProjectRuntimeRepository implements ProjectRuntimeRepository {
    private final JdbcTemplate jdbc;
    JdbcProjectRuntimeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void replace(String projectId, List<ProjectRuntime> runtimes) {
        jdbc.update("DELETE FROM project_runtime WHERE project_id = ?", UUID.fromString(projectId));
        for (ProjectRuntime runtime : runtimes) {
            jdbc.update("INSERT INTO PROJECT_RUNTIME(project_id, ordinal, value, normalized_value) VALUES (?, ?, ?, ?)",
                UUID.fromString(runtime.projectId()), runtime.ordinal(), runtime.value(), runtime.normalizedValue());
        }
    }
    public List<ProjectRuntime> findByProjectId(String projectId) {
        return jdbc.query("SELECT project_id, ordinal, value, normalized_value FROM PROJECT_RUNTIME WHERE project_id = ? ORDER BY ordinal",
            (rs, n) -> new ProjectRuntime(rs.getObject(1, UUID.class).toString(), rs.getInt(2), rs.getString(3), rs.getString(4)), UUID.fromString(projectId));
    }
}
