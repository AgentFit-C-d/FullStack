package com.agentfit.coreapi.domain.project.controller;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectDeletionService deletionService;
    private final SessionPrincipalResolver principalResolver;

    public ProjectController(ProjectService projectService, ProjectDeletionService deletionService,
            SessionPrincipalResolver principalResolver) {
        this.projectService = projectService;
        this.deletionService = deletionService;
        this.principalResolver = principalResolver;
    }

    @PostMapping
    public ResponseEntity<ProjectResponseEnvelope> create(HttpServletRequest request,
            @Valid @RequestBody ProjectCreateRequest body) {
        Project project = projectService.create(principal(request), body.name());
        return ResponseEntity.status(201).body(new ProjectResponseEnvelope(ProjectResponse.from(project)));
    }

    @GetMapping
    public ProjectListResponse list(HttpServletRequest request) {
        List<ProjectResponse> projects = projectService.list(principal(request)).stream()
            .map(ProjectResponse::from).toList();
        return new ProjectListResponse(projects);
    }

    @GetMapping("/{projectId}")
    public ProjectDetailResponse get(HttpServletRequest request, @PathVariable String projectId) {
        return new ProjectDetailResponse(ProjectResponse.from(projectService.get(principal(request), projectId)), null, null, null);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable String projectId,
            @Valid @RequestBody ProjectDeleteRequest body) {
        deletionService.delete(principal(request), projectId, body.confirmation(), body.expectedVersion());
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedPrincipal principal(HttpServletRequest request) {
        return principalResolver.resolve(request).orElseThrow(UnauthenticatedException::new);
    }

}



