package com.agentfit.coreapi.domain.analysis.entity;

import java.time.Instant;

public record AnalysisAdmission(short id, short activeCount, Instant updatedAt) { }
