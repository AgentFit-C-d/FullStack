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
import java.util.List;

public record ProjectResponse(String id, String name, long version, Instant createdAt, Instant updatedAt,
        String role, List<String> runtimes) {
    public ProjectResponse(String id, String name, long version, Instant createdAt, Instant updatedAt) {
        this(id, name, version, createdAt, updatedAt, "BACKEND", List.of());
    }

    public static ProjectResponse from(Project project) {
        return from(project, List.of());
    }

    public static ProjectResponse from(Project project, List<String> runtimes) {
        return new ProjectResponse(project.id(), project.name(), project.version(), project.createdAt(),
                project.updatedAt(), project.developerRole(), List.copyOf(runtimes == null ? List.of() : runtimes));
    }

    public static ProjectResponse from(String id, String name, long version, Instant createdAt, Instant updatedAt) {
        return new ProjectResponse(id, name, version, createdAt, updatedAt, "BACKEND", List.of());
    }
}



