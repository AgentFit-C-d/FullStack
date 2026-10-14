package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAdmission;

public interface AnalysisAdmissionRepository {
    default AnalysisAdmission lock() { return new AnalysisAdmission((short)1,(short)0,java.time.Instant.now()); }
    default void increment() { }
    default void decrement() { }
}
