package com.agentfit.coreapi.domain.project.dto.response;
import com.agentfit.coreapi.domain.project.entity.Project;
public record ProjectProfileResponse(String id, String name, long version, String role) { public static ProjectProfileResponse from(Project p) { return new ProjectProfileResponse(p.id(),p.name(),p.version(),p.developerRole()); } }
