package com.agentfit.coreapi.domain.project.entity;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.time.Instant;

public record Project(String id, long ownerId, String name, long version, String stage,
        long analysisGeneration, long reviewGeneration, Instant createdAt, Instant updatedAt) {
    public Project(String id, long ownerId, String name, long version, Instant createdAt, Instant updatedAt) {
        this(id, ownerId, name, version, "CREATED", 0L, 0L, createdAt, updatedAt);
    }
}



