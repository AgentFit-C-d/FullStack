package com.agentfit.coreapi.domain.analysis;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.project.service.ResultPersistenceGuard;

class ResultPersistenceGuardTest {
    @Test
    void acceptsOnlyLiveMatchingProjectAttempt() {
        ResultPersistenceGuard guard = new ResultPersistenceGuard(input -> input);
        assertTrue(guard.canPersist(true));
        assertFalse(guard.canPersist(false));
    }

    @Test
    void expiredDeadlineIsRejectedByAttemptContext() {
        AnalysisAttemptContext context = new AnalysisAttemptContext("r-1", "p-1", 1L, "v1",
            Instant.parse("2026-10-07T23:59:00Z"), "PROCESSING");
        assertFalse(context.isAcceptable(Instant.parse("2026-10-08T00:00:00Z")));
    }
}



