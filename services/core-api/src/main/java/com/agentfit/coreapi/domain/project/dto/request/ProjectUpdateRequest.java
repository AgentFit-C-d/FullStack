package com.agentfit.coreapi.domain.project.dto.request;
import com.fasterxml.jackson.annotation.JsonAlias;
public record ProjectUpdateRequest(String name, String role, @JsonAlias({"runtimeInput", "runtimes"}) String runtimes, long expectedVersion) { }
