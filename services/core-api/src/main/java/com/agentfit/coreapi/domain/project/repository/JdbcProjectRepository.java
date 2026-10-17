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
import java.util.UUID;

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
        UUID ownerId = requireOwnerUuid(project);
        jdbcTemplate.update("INSERT INTO project (id, user_id, name, version, current_step, analysis_generation, review_generation, developer_role, current_input_revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            UUID.fromString(project.id()), ownerId, project.name(), project.version(),
            toCurrentStep(project.stage()), project.analysisGeneration(), project.reviewGeneration(),
            project.developerRole(), project.currentInputRevision(), Timestamp.from(project.createdAt()),
            Timestamp.from(project.updatedAt()));
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
    public Optional<Project> findByIdAndOwnerUuid(String id, UUID ownerUuid) {
        return jdbcTemplate.query("SELECT id, user_id, name, version, current_step, analysis_generation, review_generation, developer_role, current_input_revision, created_at, updated_at FROM project WHERE id = ? AND user_id = ?",
            (rs, rowNum) -> mapUuid(rs.getString("id"), rs.getObject("user_id", UUID.class), rs.getString("name"),
                rs.getLong("version"), rs.getString("current_step"), rs.getLong("analysis_generation"),
                rs.getLong("review_generation"), rs.getString("developer_role"), (Long) rs.getObject("current_input_revision"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), UUID.fromString(id), ownerUuid)
            .stream().findFirst();
    }

    @Override
    public List<Project> findByOwnerUuid(UUID ownerUuid) {
        return jdbcTemplate.query("SELECT id, user_id, name, version, current_step, analysis_generation, review_generation, developer_role, current_input_revision, created_at, updated_at FROM project WHERE user_id = ? ORDER BY updated_at DESC, id ASC",
            (rs, rowNum) -> mapUuid(rs.getObject("id", UUID.class).toString(), rs.getObject("user_id", UUID.class),
                rs.getString("name"), rs.getLong("version"), rs.getString("current_step"),
                rs.getLong("analysis_generation"), rs.getLong("review_generation"),
                rs.getString("developer_role"), (Long) rs.getObject("current_input_revision"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), ownerUuid);
    }

    @Override
    public Optional<Project> findByIdForUpdate(String id) {
        return jdbcTemplate.query("SELECT id, user_id, name, version, current_step, analysis_generation, review_generation, developer_role, current_input_revision, created_at, updated_at FROM project WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> mapUuid(rs.getString("id"), rs.getObject("user_id", UUID.class), rs.getString("name"),
                rs.getLong("version"), rs.getString("current_step"), rs.getLong("analysis_generation"),
                rs.getLong("review_generation"), rs.getString("developer_role"), (Long) rs.getObject("current_input_revision"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), UUID.fromString(id))
            .stream().findFirst();
    }

    @Override
    public void updateVersion(String id, long version, java.time.Instant updatedAt) {
        jdbcTemplate.update("UPDATE project SET version = ?, updated_at = ? WHERE id = ?", version,
            Timestamp.from(updatedAt), UUID.fromString(id));
    }
    @Override
    public void updateMetadata(String id, String name, String role, long version, java.time.Instant updatedAt) {
        jdbcTemplate.update("UPDATE project SET name=?, developer_role=?, version=?, updated_at=? WHERE id=?", name, role, version, Timestamp.from(updatedAt), UUID.fromString(id));
    }
    @Override
    public void updateInputRevision(String id,long revision,long version,java.time.Instant updatedAt){ jdbcTemplate.update("UPDATE project SET current_input_revision=?,version=?,updated_at=? WHERE id=?",revision,version,Timestamp.from(updatedAt),UUID.fromString(id)); }
    @Override
    public void markAnalysisStarted(String id,long generation,long version,java.time.Instant updatedAt){jdbcTemplate.update("UPDATE project SET analysis_generation=?,version=?,updated_at=? WHERE id=?",generation,version,Timestamp.from(updatedAt),UUID.fromString(id));}

    @Override
    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM project WHERE id = ?", UUID.fromString(id));
    }

    @Override
    public void deleteProjectGraph(String id) {
        // Child tables are optional in this MVP. Tables with FK cascade are removed first
        // when present; PROJECT deletion cascades AUDIT and ANALYSIS_ATTEMPT.
        jdbcTemplate.update("DELETE FROM project WHERE id = ?", UUID.fromString(id));
    }

    private Project map(String id, long ownerId, String name, long version, String stage,
            long analysisGeneration, long reviewGeneration, java.time.Instant createdAt, java.time.Instant updatedAt,
            String developerRole, Long currentInputRevision) {
        return new Project(id, ownerId, name, version, stage, analysisGeneration, reviewGeneration, createdAt, updatedAt, developerRole, currentInputRevision);
    }
    private Project mapUuid(String id, UUID ownerUuid, String name, long version, String currentStep,
            long analysisGeneration, long reviewGeneration, String developerRole, Long currentInputRevision,
            java.time.Instant createdAt, java.time.Instant updatedAt) {
        return new Project(id, 0L, name, version, currentStep, analysisGeneration, reviewGeneration,
            createdAt, updatedAt, developerRole == null ? "BACKEND" : developerRole, currentInputRevision, ownerUuid);
    }

    private UUID requireOwnerUuid(Project project) {
        if (project.ownerUuid() == null) throw new IllegalArgumentException("V5 project owner UUID is required");
        return project.ownerUuid();
    }

    private String toCurrentStep(String stage) {
        if (stage == null || stage.isBlank() || "CREATED".equals(stage)) return "PLANNING";
        return switch (stage) {
            case "PLANNING", "ROLE_ENVIRONMENT", "RECOMMENDATION", "CONFIGURATION", "COMPLETED" -> stage;
            default -> "PLANNING";
        };
    }
}



