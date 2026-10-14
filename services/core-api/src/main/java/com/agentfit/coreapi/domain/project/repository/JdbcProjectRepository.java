package com.agentfit.coreapi.domain.project.repository;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProjectRepository implements ProjectRepository {
    private final JdbcTemplate jdbcTemplate;

    JdbcProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Project save(Project project) {
        jdbcTemplate.update("INSERT INTO PROJECT (id, owner_id, name, version, stage, analysis_generation, review_generation, created_at, updated_at, developer_role, current_input_revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            project.id(), project.ownerId(), project.name(), project.version(), project.stage(),
            project.analysisGeneration(), project.reviewGeneration(),
            Timestamp.from(project.createdAt()), Timestamp.from(project.updatedAt()), project.developerRole(), project.currentInputRevision());
        return project;
    }

    @Override
    public List<Project> findByOwnerId(long ownerId) {
        return jdbcTemplate.query("SELECT id, owner_id, name, version, stage, analysis_generation, review_generation, created_at, updated_at, developer_role, current_input_revision FROM PROJECT WHERE owner_id = ? ORDER BY updated_at DESC, id ASC",
            (rs, rowNum) -> map(rs.getString("id"), rs.getLong("owner_id"), rs.getString("name"),
                rs.getLong("version"), rs.getString("stage"), rs.getLong("analysis_generation"),
                rs.getLong("review_generation"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getString("developer_role"), (Long) rs.getObject("current_input_revision")), ownerId);
    }

    @Override
    public Optional<Project> findByIdAndOwnerId(String id, long ownerId) {
        return jdbcTemplate.query("SELECT id, owner_id, name, version, stage, analysis_generation, review_generation, created_at, updated_at, developer_role, current_input_revision FROM PROJECT WHERE id = ? AND owner_id = ?",
            (rs, rowNum) -> map(rs.getString("id"), rs.getLong("owner_id"), rs.getString("name"),
                rs.getLong("version"), rs.getString("stage"), rs.getLong("analysis_generation"),
                rs.getLong("review_generation"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getString("developer_role"), (Long) rs.getObject("current_input_revision")), id, ownerId)
            .stream().findFirst();
    }

    @Override
    public Optional<Project> findByIdForUpdate(String id) {
        return jdbcTemplate.query("SELECT id, owner_id, name, version, stage, analysis_generation, review_generation, created_at, updated_at, developer_role, current_input_revision FROM PROJECT WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> map(rs.getString("id"), rs.getLong("owner_id"), rs.getString("name"),
                rs.getLong("version"), rs.getString("stage"), rs.getLong("analysis_generation"),
                rs.getLong("review_generation"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getString("developer_role"), (Long) rs.getObject("current_input_revision")), id)
            .stream().findFirst();
    }

    @Override
    public void updateVersion(String id, long version, java.time.Instant updatedAt) {
        jdbcTemplate.update("UPDATE PROJECT SET version = ?, updated_at = ? WHERE id = ?", version,
            Timestamp.from(updatedAt), id);
    }
    @Override
    public void updateMetadata(String id, String name, String role, long version, java.time.Instant updatedAt) {
        jdbcTemplate.update("UPDATE PROJECT SET name=?, developer_role=?, version=?, updated_at=? WHERE id=?", name, role, version, Timestamp.from(updatedAt), id);
    }
    @Override
    public void updateInputRevision(String id,long revision,long version,java.time.Instant updatedAt){ jdbcTemplate.update("UPDATE PROJECT SET current_input_revision=?,version=?,updated_at=? WHERE id=?",revision,version,Timestamp.from(updatedAt),id); }
    @Override
    public void markAnalysisStarted(String id,long generation,long version,java.time.Instant updatedAt){jdbcTemplate.update("UPDATE PROJECT SET analysis_generation=?,stage='PROCESSING',version=?,updated_at=? WHERE id=?",generation,version,Timestamp.from(updatedAt),id);}

    @Override
    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM PROJECT WHERE id = ?", id);
    }

    @Override
    public void deleteProjectGraph(String id) {
        // Child tables are optional in this MVP. Tables with FK cascade are removed first
        // when present; PROJECT deletion cascades AUDIT and ANALYSIS_ATTEMPT.
        jdbcTemplate.update("DELETE FROM PROJECT WHERE id = ?", id);
    }

    private Project map(String id, long ownerId, String name, long version, String stage,
            long analysisGeneration, long reviewGeneration, java.time.Instant createdAt, java.time.Instant updatedAt,
            String developerRole, Long currentInputRevision) {
        return new Project(id, ownerId, name, version, stage, analysisGeneration, reviewGeneration, createdAt, updatedAt, developerRole, currentInputRevision);
    }
}



