package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisInputLease;
import java.time.Instant;
import java.util.Optional;

public interface AnalysisInputLeaseRepository {
    default AnalysisInputLease save(AnalysisInputLease lease) {
        return lease;
    }

    default Optional<AnalysisInputLease> findByAttemptId(long attemptId) {
        return Optional.empty();
    }

    default Optional<AnalysisInputLease> findByUploadId(String uploadId) {
        return Optional.empty();
    }

    default Optional<AnalysisInputLease> findUsable(
            String uploadId,
            long attemptId,
            String leaseId,
            Instant now) {
        return findByUploadId(uploadId)
                .filter(lease -> lease.attemptId() == attemptId)
                .filter(lease -> lease.leaseId().equals(leaseId))
                .filter(lease -> lease.isUsableAt(now));
    }
}
