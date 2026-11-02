package com.agentfit.coreapi.domain.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisInputLease;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisInputLeaseRepository;
import com.agentfit.coreapi.domain.analysis.service.AnalysisExecutionScheduler;
import com.agentfit.coreapi.domain.analysis.service.AnalysisExecutionService;
import com.agentfit.coreapi.domain.analysis.service.AnalysisWorkerTerminalCoordinator;
import com.agentfit.coreapi.domain.analysis.service.InternalInputAccessService;
import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import com.agentfit.coreapi.domain.project.repository.PlanningInputRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisExecutionSchedulerTest {
    @Test
    void claimsOnceAndPersistsDraftThroughTerminalWithInputReference() {
        Instant now = Instant.now();
        AnalysisAttempt attempt = new AnalysisAttempt(UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), "request-1", 1, "1.0", "PROCESSING",
            now.plusSeconds(120), now, null, UUID.randomUUID(), 1L, null)
            .withClaim(UUID.randomUUID(), now, now.plusSeconds(180));
        AnalysisAttemptRepository attempts = mock(AnalysisAttemptRepository.class);
        when(attempts.claimNext(any(), any())).thenReturn(Optional.of(attempt), Optional.empty());
        PlanningInput input = new PlanningInput(attempt.projectId(), 1L, "PDF", null,
            "application/pdf", 4L, 4, null, "%PDF".getBytes(StandardCharsets.UTF_8), null, null, now);
        PlanningInputRepository inputs = mock(PlanningInputRepository.class);
        when(inputs.findByProjectIdAndRevision(attempt.projectId(), 1L)).thenReturn(Optional.of(input));
        AnalysisInputLeaseRepository leases = mock(AnalysisInputLeaseRepository.class);
        when(leases.findByAttemptId(attempt.id())).thenReturn(Optional.empty());
        AnalysisInputLease lease = new AnalysisInputLease(attempt.id(), "upload-1", "lease-1",
            attempt.projectId(), 1L, attempt.deadlineAt(), now);
        InternalInputAccessService inputAccess = mock(InternalInputAccessService.class);
        when(inputAccess.issueLease(eq(attempt), eq(1L), eq(attempt.deadlineAt()))).thenReturn(lease);
        AnalysisWorkerTerminalCoordinator terminal = mock(AnalysisWorkerTerminalCoordinator.class);
        int[] calls = {0};
        AiAnalysisClient client = (context, payload) -> {
            calls[0]++;
            assertTrue(payload.containsKey("inputRef"));
            assertEquals("pdf", payload.get("inputKind"));
            assertEquals(attempt.id(), payload.get("attemptId"));
            assertNotNull(attempt.executionToken());
            return new AiAnalysisClient.AnalysisResponse(context.requestId(), context.generation(),
                context.contractVersion(), Map.of("profile", Map.of("project_name", "sample")));
        };
        AnalysisExecutionScheduler scheduler = new AnalysisExecutionScheduler(attempts, leases,
            inputs, inputAccess, new AnalysisExecutionService(client), terminal,
            Clock.fixed(now, ZoneOffset.UTC), 180);

        scheduler.executePending();
        scheduler.executePending();

        assertEquals(1, calls[0]);
        verify(terminal, times(1)).succeed(eq(attempt), any());
    }
}
