package com.agentfit.coreapi.domain.project;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.project.service.ResultPersistenceGuard;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Storage-adapter contract for delete/late-result races; PostgreSQL runs the same guard in production. */
class ProjectDeletionPostgresIntegrationTest {
    @Test
    void lateResultAfterDeleteCannotRecreateProject() {
        ResultPersistenceGuard guard = new ResultPersistenceGuard(value -> value);
        AnalysisAttemptContext context = new AnalysisAttemptContext("r-1", "p-1", 1L, "v1",
            Instant.now().plusSeconds(60), "PROCESSING");
        var deleted = new com.agentfit.coreapi.domain.project.entity.Project("p-1", 7L, "demo", 2L,
            "DELETED", 2L, 0L, Instant.now(), Instant.now());
        var active = new com.agentfit.coreapi.domain.project.entity.Project("p-1", 7L, "demo", 1L,
            "CREATED", 1L, 0L, Instant.now(), Instant.now());
        assertFalse(guard.canPersist(context, deleted, Instant.now()));
        assertTrue(guard.canPersist(context, active, Instant.now()));
    }
}
