package com.agentfit.coreapi.domain.project.repository;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    Project save(Project project);

    List<Project> findByOwnerId(long ownerId);

    Optional<Project> findByIdAndOwnerId(String id, long ownerId);

    default Optional<Project> findByIdForUpdate(String id) {
        return Optional.empty();
    }

    default void updateVersion(String id, long version, java.time.Instant updatedAt) {
        // In-memory repositories used by unit tests may not need persistence.
    }
    default void updateMetadata(String id, String name, String role, long version, java.time.Instant updatedAt) { updateVersion(id, version, updatedAt); }
    default void updateInputRevision(String id, long revision, long version, java.time.Instant updatedAt) { updateVersion(id, version, updatedAt); }
    default void markAnalysisStarted(String id,long generation,long version,java.time.Instant updatedAt){updateVersion(id,version,updatedAt);}

    default void deleteProjectGraph(String id) {
        deleteById(id);
    }

    default void deleteById(String id) {
        throw new UnsupportedOperationException("deleteById is not implemented");
    }
}



