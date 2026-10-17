package com.agentfit.coreapi.domain.project.repository;

import com.agentfit.coreapi.domain.project.entity.ProjectCreationIdempotency;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProjectCreationIdempotencyRepository implements ProjectCreationIdempotencyRepository {
    private final JdbcTemplate jdbc;
    JdbcProjectCreationIdempotencyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<ProjectCreationIdempotency> find(long ownerId, String operation, String key) {
        try { return Optional.ofNullable(jdbc.queryForObject("SELECT owner_id,operation,idempotency_key,request_fingerprint,project_id,created_at FROM PROJECT_CREATION_IDEMPOTENCY WHERE owner_id=? AND operation=? AND idempotency_key=?", (rs,n) -> new ProjectCreationIdempotency(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant()), ownerId, operation, key)); }
        catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }
    public void reserve(ProjectCreationIdempotency v) { jdbc.update("INSERT INTO PROJECT_CREATION_IDEMPOTENCY(owner_id,operation,idempotency_key,request_fingerprint,project_id,created_at) VALUES (?,?,?,?,?,?)", v.ownerId(),v.operation(),v.idempotencyKey(),v.requestFingerprint(),v.projectId(),Timestamp.from(v.createdAt())); }
    public boolean reserveIfAbsent(ProjectCreationIdempotency v) { return jdbc.update("INSERT INTO PROJECT_CREATION_IDEMPOTENCY(owner_id,operation,idempotency_key,request_fingerprint,project_id,created_at) VALUES (?,?,?,?,?,?) ON CONFLICT (owner_id,operation,idempotency_key) DO NOTHING", v.ownerId(),v.operation(),v.idempotencyKey(),v.requestFingerprint(),v.projectId(),Timestamp.from(v.createdAt())) == 1; }
    public void linkProject(long ownerId,String operation,String key,String projectId) { jdbc.update("UPDATE PROJECT_CREATION_IDEMPOTENCY SET project_id=? WHERE owner_id=? AND operation=? AND idempotency_key=?",projectId,ownerId,operation,key); }
    @Override
    public Optional<ProjectCreationIdempotency> findByOwnerUuid(UUID ownerId, String operation, String key) {
        try { return Optional.ofNullable(jdbc.queryForObject("SELECT owner_id,operation,idempotency_key,request_fingerprint,project_id,created_at FROM project_creation_idempotency WHERE owner_id=? AND operation=? AND idempotency_key=?", (rs,n) -> new ProjectCreationIdempotency(rs.getObject(1, UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getObject(5, UUID.class) == null ? null : rs.getObject(5, UUID.class).toString(),rs.getTimestamp(6).toInstant()), ownerId, operation, key)); }
        catch (EmptyResultDataAccessException e) { return Optional.empty(); }
    }
    @Override
    public void reserveByOwnerUuid(ProjectCreationIdempotency value) {
        jdbc.update("INSERT INTO project_creation_idempotency(owner_id,operation,idempotency_key,request_fingerprint,project_id,created_at) VALUES (?,?,?,?,?,?)", value.ownerUuid(),value.operation(),value.idempotencyKey(),value.requestFingerprint(),value.projectId() == null ? null : UUID.fromString(value.projectId()),Timestamp.from(value.createdAt()));
    }
    @Override
    public boolean reserveIfAbsentByOwnerUuid(ProjectCreationIdempotency value) {
        return jdbc.update("INSERT INTO project_creation_idempotency(owner_id,operation,idempotency_key,request_fingerprint,project_id,created_at) VALUES (?,?,?,?,?,?) ON CONFLICT (owner_id,operation,idempotency_key) DO NOTHING", value.ownerUuid(),value.operation(),value.idempotencyKey(),value.requestFingerprint(),value.projectId() == null ? null : UUID.fromString(value.projectId()),Timestamp.from(value.createdAt())) == 1;
    }
    @Override
    public void linkProjectByOwnerUuid(UUID ownerId, String operation, String key, String projectId) {
        jdbc.update("UPDATE project_creation_idempotency SET project_id=? WHERE owner_id=? AND operation=? AND idempotency_key=?", UUID.fromString(projectId),ownerId,operation,key);
    }
}
