package com.agentfit.coreapi.domain.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.analysis.service.AnalysisExecutionService;
import com.agentfit.coreapi.domain.analysis.service.AnalysisWorker;
import com.agentfit.coreapi.domain.analysis.service.AnalysisWorkerTerminalCoordinator;
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

class AnalysisWorkerTest {

    @Test
    void twoWorkersClaimOneAttemptAndCallAiOnlyOnce() {
        Instant now = Instant.now();
        AttemptRepository attempts = new AttemptRepository(attempt(now));
        Inputs inputs = new Inputs();
        int[] calls = {0};
        AiAnalysisClient client = (context, input) -> {
            calls[0]++;
            return new AiAnalysisClient.AnalysisResponse(context.requestId(), context.generation(),
                context.contractVersion(), Map.of("profile", Map.of("project_name", "demo")));
        };
        Terminal terminal = new Terminal();
        AnalysisWorker first = new AnalysisWorker(attempts, inputs, new AnalysisExecutionService(client), terminal,
            Clock.fixed(now, ZoneOffset.UTC), 180);
        AnalysisWorker second = new AnalysisWorker(attempts, inputs, new AnalysisExecutionService(client), terminal,
            Clock.fixed(now, ZoneOffset.UTC), 180);

        first.processOne();
        second.processOne();

        assertEquals(1, calls[0]);
        assertEquals(1, terminal.succeeded);
        assertEquals(0, terminal.failed);
    }

    private AnalysisAttempt attempt(Instant now) {
        return new AnalysisAttempt(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "request-1", 1,
            "1.0", "PROCESSING", now.plusSeconds(120), now, null, UUID.randomUUID(), 1L, null);
    }

    private static final class AttemptRepository implements AnalysisAttemptRepository {
        private AnalysisAttempt pending;
        private AttemptRepository(AnalysisAttempt pending) { this.pending = pending; }
        @Override public Optional<AnalysisAttempt> claimNext(Instant now, Instant leaseExpiresAt) {
            if (pending == null) return Optional.empty();
            AnalysisAttempt claimed = pending.withClaim(UUID.randomUUID(), now, leaseExpiresAt);
            pending = null;
            return Optional.of(claimed);
        }
    }

    private static final class Inputs implements PlanningInputRepository {
        @Override public Optional<PlanningInput> findByProjectIdAndRevision(String projectId, long revision) {
            return Optional.of(new PlanningInput(projectId, revision, "TEXT", null, "text/plain", 4L, 4, null,
                "plan".getBytes(StandardCharsets.UTF_8), "plan", null, Instant.now()));
        }
    }

    private static final class Terminal implements AnalysisWorkerTerminalCoordinator {
        private int succeeded;
        private int failed;
        @Override public void succeed(AnalysisAttempt attempt, AiAnalysisClient.AnalysisResponse response) { succeeded++; }
        @Override public void fail(AnalysisAttempt attempt, String errorCode) { failed++; }
    }
}
