package com.agentfit.coreapi.domain.project.controller;


import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Project lifecycle, planning input, and analysis requests")
public class ProjectController {
    private final ProjectService projectService;
    private final SessionPrincipalResolver principalResolver;
    private final ProfileConfirmationService profileConfirmationService;
    private final ProjectProfileReader profileReader;

    @Autowired
    public ProjectController(ProjectService projectService, SessionPrincipalResolver principalResolver,
            ObjectProvider<ProfileConfirmationService> profileConfirmationService,
            ObjectProvider<ProjectProfileReader> profileReader) {
        this.projectService = projectService;
        this.principalResolver = principalResolver;
        this.profileConfirmationService = profileConfirmationService.getIfAvailable();
        this.profileReader = profileReader.getIfAvailable();
    }

    public ProjectController(ProjectService projectService, SessionPrincipalResolver principalResolver) {
        this.projectService = projectService;
        this.principalResolver = principalResolver;
        this.profileConfirmationService = null;
        this.profileReader = null;
    }

    @PostMapping
    public ResponseEntity<ProjectResponseEnvelope> create(HttpServletRequest request,
            @Valid @RequestBody ProjectCreateRequest body) {
        Project project = projectService.create(principal(request), body.name());
        return ResponseEntity.status(201).body(new ProjectResponseEnvelope(response(project)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectResponseEnvelope> createMultipart(HttpServletRequest request,
            @RequestPart("metadata") ProjectCreateMetadataRequest metadata,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "inputText", required = false) String inputText,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Project project = projectService.create(principal(request), metadata.name(), metadata.role(), metadata.runtimes(), file, inputText, idempotencyKey);
        return ResponseEntity.status(201).body(new ProjectResponseEnvelope(response(project)));
    }

    @GetMapping
    public ProjectListResponse list(HttpServletRequest request) {
        List<ProjectResponse> projects = projectService.list(principal(request)).stream()
            .map(this::response).toList();
        return new ProjectListResponse(projects);
    }

    @GetMapping("/{projectId}")
    public ProjectDetailResponse get(HttpServletRequest request, @PathVariable String projectId) {
        var principal = principal(request);
        ProjectDetailResponse detail = projectService.detail(principal, projectId);
        ProjectProfileReader.Profiles profiles = profileReader == null
            ? new ProjectProfileReader.Profiles(null, null) : profileReader.read(projectId);
        return detail != null
            ? new ProjectDetailResponse(detail.project(),
                profiles.confirmed() == null ? detail.confirmed() : profiles.confirmed(),
                profiles.draft() == null ? detail.draft() : profiles.draft(), detail.latestAttempt())
            : new ProjectDetailResponse(response(projectService.get(principal, projectId)), profiles.confirmed(), profiles.draft(), null);
    }

    @PatchMapping("/{projectId}")
    public ProjectDetailResponse update(HttpServletRequest request, @PathVariable String projectId, @RequestBody ProjectUpdateRequest body) {
        projectService.update(principal(request), projectId, body);
        return projectService.detail(principal(request), projectId);
    }

    @PutMapping("/{projectId}/planning-input")
    public PlanningInputMetadataResponse replaceInput(HttpServletRequest request, @PathVariable String projectId,
            @RequestParam("expectedVersion") long expectedVersion, @RequestPart(value="file",required=false) MultipartFile file,
            @RequestParam(value="inputText",required=false) String inputText) {
        return projectService.replaceInput(principal(request),projectId,expectedVersion,file,inputText);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable String projectId,
            @Valid @RequestBody ProjectDeleteRequest body) {
        projectService.delete(principal(request), projectId, body.confirmation(), body.expectedVersion());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/profile/confirm")
    public ProfileConfirmationService.Confirmation confirmProfile(HttpServletRequest request,
            @PathVariable String projectId,
            @RequestBody ProfileConfirmationService.ConfirmRequest body) {
        if (profileConfirmationService == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "profile confirmation workflow is unavailable");
        }
        return profileConfirmationService.confirm(principal(request), projectId, body);
    }

    private AuthenticatedPrincipal principal(HttpServletRequest request) {
        return principalResolver.resolve(request).orElseThrow(UnauthenticatedException::new);
    }

    private ProjectResponse response(Project project) {
        ProjectResponse response = projectService.response(project);
        return response == null ? ProjectResponse.from(project) : response;
    }

}



