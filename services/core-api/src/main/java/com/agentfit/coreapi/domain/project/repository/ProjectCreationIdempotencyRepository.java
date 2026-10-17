package com.agentfit.coreapi.domain.project.repository;

import com.agentfit.coreapi.domain.project.entity.ProjectCreationIdempotency;
import java.util.Optional;
import java.util.UUID;

public interface ProjectCreationIdempotencyRepository {
    default Optional<ProjectCreationIdempotency> find(long ownerId, String operation, String key) { return Optional.empty(); }
    default void reserve(ProjectCreationIdempotency value) { }
    default boolean reserveIfAbsent(ProjectCreationIdempotency value) { reserve(value); return true; }
    default void linkProject(long ownerId, String operation, String key, String projectId) { }
    default Optional<ProjectCreationIdempotency> findByOwnerUuid(UUID ownerId, String operation, String key) { return Optional.empty(); }
    default void reserveByOwnerUuid(ProjectCreationIdempotency value) { }
    default boolean reserveIfAbsentByOwnerUuid(ProjectCreationIdempotency value) { reserveByOwnerUuid(value); return true; }
    default void linkProjectByOwnerUuid(UUID ownerId, String operation, String key, String projectId) { }
}
