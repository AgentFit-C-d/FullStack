package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisInputLease;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisInputLeaseRepository;
import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import com.agentfit.coreapi.domain.project.repository.PlanningInputRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InternalInputAccessService {
    private final AnalysisAttemptRepository attempts;
    private final AnalysisInputLeaseRepository leases;
    private final PlanningInputRepository inputs;
    private final Clock clock;
    private final String aiServiceToken;

    public InternalInputAccessService(
            AnalysisAttemptRepository attempts,
            AnalysisInputLeaseRepository leases,
            PlanningInputRepository inputs,
            Clock clock,
            @Value("${agentfit.internal.ai-service-token:}") String aiServiceToken) {
        this.attempts = attempts;
        this.leases = leases;
        this.inputs = inputs;
        this.clock = clock;
        this.aiServiceToken = aiServiceToken;
    }

    public AnalysisInputLease issueLease(
            AnalysisAttempt attempt,
            long inputRevision,
            Instant expiresAt) {
        AnalysisInputLease lease = new AnalysisInputLease(
                attempt.id(),
                randomId("upload"),
                randomId("lease"),
                attempt.projectId(),
                inputRevision,
                expiresAt,
                Instant.now(clock));
        return leases.save(lease);
    }

    public PlanningInput requireInput(
            String authorization,
            String uploadId,
            long attemptId,
            String leaseId) {
        requireServiceToken(authorization);
        AnalysisInputLease lease = leases.findUsable(
                        uploadId,
                        attemptId,
                        leaseId,
                        Instant.now(clock))
                .orElseThrow(() -> new IllegalArgumentException("Input lease is invalid"));
        AnalysisAttempt attempt = attempts.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis attempt is not found"));
        if (!"PROCESSING".equals(attempt.state())
                || !attempt.projectId().equals(lease.projectId())
                || !Long.valueOf(lease.inputRevision()).equals(attempt.inputRevision())) {
            throw new IllegalArgumentException("Input lease is not bound to the analysis attempt");
        }
        return inputs.findByProjectIdAndRevision(
                        lease.projectId(),
                        lease.inputRevision())
                .orElseThrow(() -> new IllegalArgumentException("Planning input is not found"));
    }

    public Map<String, Object> reserveBudget(
            String authorization,
            long attemptId,
            String leaseId,
            int pages) {
        requireServiceToken(authorization);
        if (pages < 1 || pages > 100) {
            throw new IllegalArgumentException("Invalid analysis page count");
        }
        AnalysisInputLease lease = leases.findByAttemptId(attemptId)
                .filter(value -> value.leaseId().equals(leaseId))
                .filter(value -> value.isUsableAt(Instant.now(clock)))
                .orElseThrow(() -> new IllegalArgumentException("Input lease is invalid"));
        attempts.findById(attemptId)
                .filter(attempt -> "PROCESSING".equals(attempt.state()))
                .orElseThrow(() -> new IllegalArgumentException("Analysis attempt is not accepting budget"));
        return Map.of(
                "reservationId", "budget-" + lease.leaseId(),
                "pages", pages,
                "amountMicroUsd", Math.max(1, pages));
    }

    public Map<String, Object> metadata(
            String authorization,
            String uploadId,
            long attemptId,
            String leaseId) {
        PlanningInput input = requireInput(authorization, uploadId, attemptId, leaseId);
        return Map.of(
                "kind", toInputKind(input.inputType()),
                "size", input.rawContent() == null ? 0 : input.rawContent().length);
    }

    public Map<String, Object> reference(
            String authorization,
            String uploadId,
            long attemptId,
            String leaseId) {
        PlanningInput input = requireInput(authorization, uploadId, attemptId, leaseId);
        AnalysisInputLease lease = leases.findUsable(
                        uploadId,
                        attemptId,
                        leaseId,
                        Instant.now(clock))
                .orElseThrow(() -> new IllegalArgumentException("Input lease is invalid"));
        return Map.of(
                "uploadId", lease.uploadId(),
                "storage", "local",
                "objectKey", "inputs/" + lease.uploadId(),
                "expiresAt", lease.expiresAt().toString());
    }

    private void requireServiceToken(String authorization) {
        String expected = "Bearer " + aiServiceToken;
        if (aiServiceToken.isBlank()
                || authorization == null
                || !MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Internal input authorization failed");
        }
    }

    private String randomId(String prefix) {
        return prefix + "-" + HexFormat.of().formatHex(
                java.util.UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    }

    private String toInputKind(String inputType) {
        return switch (inputType) {
            case "PDF" -> "pdf";
            case "MARKDOWN" -> "markdown";
            case "TEXT" -> "text";
            default -> throw new IllegalArgumentException("Unsupported planning input type");
        };
    }
}
