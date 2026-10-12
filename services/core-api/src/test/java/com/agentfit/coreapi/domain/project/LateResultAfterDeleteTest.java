package com.agentfit.coreapi.domain.project;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class LateResultAfterDeleteTest {
    @Test
    void deletedProjectCannotAcceptLateResult() {
        ResultPersistenceGuard guard = new ResultPersistenceGuard(projectExists -> projectExists);
        assertFalse(guard.canPersist(false));
    }

    @Test
    void logoutDoesNotCancelLiveServerOwnedAttempt() {
        ResultPersistenceGuard guard = new ResultPersistenceGuard(value -> value);
        var context = new com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext(
            "r-1", "p-1", 1L, "v1", Instant.now().plusSeconds(60), "PROCESSING");
        var project = new com.agentfit.coreapi.domain.project.entity.Project("p-1", 7L, "demo", 1L,
            "CREATED", 1L, 0L, Instant.now(), Instant.now());
        assertTrue(guard.canPersist(context, project, Instant.now()));
    }
}



