package com.agentfit.coreapi.domain.audit.service;

import com.agentfit.coreapi.domain.audit.entity.AuditEvent;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records a minimal failure/denial event after the business transaction has rolled back. */
@Service
public class FailureAuditService {
    private final AuditRecorder recorder;

    public FailureAuditService(AuditRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(AuditEvent event) {
        return recorder.record(event);
    }
}
