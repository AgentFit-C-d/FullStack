package com.agentfit.coreapi.domain.project.repository;

import com.agentfit.coreapi.domain.project.entity.ProjectRuntime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProjectRuntimeRepository implements ProjectRuntimeRepository {
    private final JdbcTemplate jdbc;
    JdbcProjectRuntimeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void replace(String projectId, List<ProjectRuntime> runtimes) {
        jdbc.update("DELETE FROM PROJECT_RUNTIME WHERE project_id = ?", projectId);
        for (ProjectRuntime runtime : runtimes) {
            jdbc.update("INSERT INTO PROJECT_RUNTIME(project_id, ordinal, value, normalized_value) VALUES (?, ?, ?, ?)",
                runtime.projectId(), runtime.ordinal(), runtime.value(), runtime.normalizedValue());
        }
    }
    public List<ProjectRuntime> findByProjectId(String projectId) {
        return jdbc.query("SELECT project_id, ordinal, value, normalized_value FROM PROJECT_RUNTIME WHERE project_id = ? ORDER BY ordinal",
            (rs, n) -> new ProjectRuntime(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4)), projectId);
    }
}
