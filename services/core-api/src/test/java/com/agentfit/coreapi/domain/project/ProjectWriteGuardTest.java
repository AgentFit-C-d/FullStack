package com.agentfit.coreapi.domain.project;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.global.exception.VersionConflictException;

class ProjectWriteGuardTest {
    @Test
    void staleProtectedWriteIsNotExecuted() {
        Instant now = Instant.parse("2026-10-08T00:00:00Z");
        Project project = new Project("p-1", 7L, "demo", 4L, now, now);
        ProjectRepository repository = new ProjectRepository() {
            @Override public Project save(Project value) { return value; }
            @Override public java.util.List<Project> findByOwnerId(long ownerId) { return java.util.List.of(project); }
            @Override public Optional<Project> findByIdAndOwnerId(String id, long ownerId) {
                return id.equals("p-1") && ownerId == 7L ? Optional.of(project) : Optional.empty();
            }
        };
        ProjectWriteGuard guard = new ProjectWriteGuard(new ProjectOwnershipGuard(repository));

        assertThrows(VersionConflictException.class, () -> guard.execute(
            new AuthenticatedPrincipal(7L, "owner", null), new ProjectWriteCommand("p-1", 3L),
            ignored -> { throw new AssertionError("stale write must not run"); }));
        assertEquals(4L, project.version());
    }
}



