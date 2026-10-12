package com.agentfit.coreapi.domain.project.dto.response;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.time.Instant;

public record ProjectResponse(String id, String name, long version, Instant createdAt, Instant updatedAt) {
    public static ProjectResponse from(Project project) {
        return from(project.id(), project.name(), project.version(), project.createdAt(), project.updatedAt());
    }

    public static ProjectResponse from(String id, String name, long version, Instant createdAt, Instant updatedAt) {
        return new ProjectResponse(id, name, version, createdAt, updatedAt);
    }
}



