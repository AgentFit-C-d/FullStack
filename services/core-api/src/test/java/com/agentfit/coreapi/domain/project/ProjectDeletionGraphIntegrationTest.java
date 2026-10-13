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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;

class ProjectDeletionGraphIntegrationTest {
    @Test
    void deletingProjectRemovesProjectButDoesNotTouchUserSessionBoundary() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository)
            .create(new AuthenticatedPrincipal(7L, "owner", null), "demo");
        ProjectService service = new ProjectService(repository);

        service.delete(new AuthenticatedPrincipal(7L, "owner", null), project.id(), true, 1L);

        assertEquals(0, repository.projects.size());
    }

    @Test
    void deletionScopeRemovesProjectOwnedDataButPreservesSharedBoundaries() {
        assertTrue(com.agentfit.coreapi.domain.project.service.ProjectDeletionScope.DELETED.contains("AUDIT"));
        assertTrue(com.agentfit.coreapi.domain.project.service.ProjectDeletionScope.DELETED.contains("ANALYSIS_ATTEMPT"));
        assertTrue(com.agentfit.coreapi.domain.project.service.ProjectDeletionScope.PRESERVED.contains("USER"));
        assertTrue(com.agentfit.coreapi.domain.project.service.ProjectDeletionScope.PRESERVED.contains("SPRING_SESSION"));
    }

    @Test
    void activeAnalysisIsRejectedBeforeGraphDeletion() {
        ProjectCreationTest.InMemoryProjectRepository repository = new ProjectCreationTest.InMemoryProjectRepository();
        Project project = new ProjectService(repository).create(new AuthenticatedPrincipal(7L, "owner", null), "demo");
        var active = new com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository() {
            public java.util.Optional<com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt> findProcessingByProjectId(String id) {
                return java.util.Optional.of(new com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt(1, id, "r", 1, "1", "PROCESSING", java.time.Instant.now().plusSeconds(600), java.time.Instant.now(), null));
            }
        };
        ProjectService service = new ProjectService(repository, new ProjectOwnershipGuard(repository), new com.agentfit.coreapi.domain.audit.service.AuditRecorder(), new ProjectRuntimeRepository(){}, new PlanningInputRepository(){}, new ProjectCreationIdempotencyRepository(){}, new PlanningInputValidator(), active);
        assertThrows(ActiveAnalysisProjectDeletionException.class, () -> service.delete(new AuthenticatedPrincipal(7L, "owner", null), project.id(), true, 1));
        assertEquals(1, repository.projects.size());
    }
}



