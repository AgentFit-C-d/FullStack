package com.agentfit.coreapi.recommendation.controller;

import com.agentfit.coreapi.catalog.CatalogPreviewRequest;
import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** A's authenticated boundary around the unchanged B recommendation/Preview services. */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class RecommendationController {
    private final SessionPrincipalResolver principals;
    private final ProjectOwnershipGuard ownership;
    private final ObjectProvider<RecommendationCreationService> recommendations;
    private final ObjectProvider<RecommendationLatestDetailsService> latest;
    private final ObjectProvider<PreviewCreationService> previews;

    public RecommendationController(SessionPrincipalResolver principals, ProjectOwnershipGuard ownership,
            ObjectProvider<RecommendationCreationService> recommendations,
            ObjectProvider<RecommendationLatestDetailsService> latest,
            ObjectProvider<PreviewCreationService> previews) {
        this.principals = principals;
        this.ownership = ownership;
        this.recommendations = recommendations;
        this.latest = latest;
        this.previews = previews;
    }

    @PostMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> createRecommendation(HttpServletRequest request,
            @PathVariable String projectId, @RequestBody RecommendationRequest body) {
        AuthenticatedPrincipal principal = principal(request);
        ownership.requireOwned(principal, projectId);
        RecommendationCreationService service = required(recommendations, "recommendation workflow");
        var created = service.create(projectId, new RecommendationCreationService.ExpectedVersions(
            body.expectedVersions().project(), body.expectedVersions().developer(),
            body.expectedVersions().environment()));
        return ResponseEntity.status(HttpStatus.CREATED).body(recommendationResponse(created));
    }

    @GetMapping("/recommendations/latest")
    public Map<String, Object> latest(HttpServletRequest request, @PathVariable String projectId) {
        AuthenticatedPrincipal principal = principal(request);
        ownership.requireOwned(principal, projectId);
        var snapshot = required(latest, "latest recommendation workflow").latest(projectId);
        if (snapshot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "recommendation not found");
        return Map.of("recommendationId", snapshot.summary().recommendationId(),
            "status", snapshot.summary().status().name(), "validity", snapshot.summary().validity().name(),
            "basis", snapshot.summary().basis(), "items", snapshot.catalogItems(),
            "capabilities", snapshot.capabilities(), "notices", snapshot.notices());
    }

    @PostMapping("/config-previews")
    public ResponseEntity<Map<String, Object>> createPreview(HttpServletRequest request,
            @PathVariable String projectId, @RequestBody PreviewRequest body) {
        AuthenticatedPrincipal principal = principal(request);
        ownership.requireOwned(principal, projectId);
        var created = required(previews, "Preview workflow").create(projectId,
            new CatalogPreviewRequest(body.basis(), body.recommendationId(), body.target(),
                body.selectedToolKeys(), body.permissionSelections(), body.existingState(),
                body.providedFiles(), body.generatorVersion()));
        var saved = created.saved();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "previewId", saved.previewId(), "recommendationId", body.recommendationId(),
            "fingerprint", saved.fingerprint(), "selectionDigest", created.preview().selectionDigest(),
            "contentDigest", created.preview().contentDigest(), "createdAt", saved.createdAt(),
            "expiresAt", saved.expiresAt()));
    }

    private AuthenticatedPrincipal principal(HttpServletRequest request) {
        return principals.resolve(request).orElseThrow(UnauthenticatedException::new);
    }

    private static <T> T required(ObjectProvider<T> provider, String name) {
        T value = provider.getIfAvailable();
        if (value == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, name + " is unavailable");
        return value;
    }

    private static Map<String, Object> recommendationResponse(RecommendationCreationService.Created created) {
        var result = created.result();
        return Map.of("recommendationId", created.recommendationId(),
            "status", result.decision().status().name(), "basis", created.basis(),
            "capabilities", result.capabilities(), "items", result.items(),
            "notices", result.clarificationQuestions());
    }

    public record ExpectedVersions(long project, long developer, long environment) {}
    public record RecommendationRequest(ExpectedVersions expectedVersions) {}

    public record PreviewRequest(PreviewBasis basis, String recommendationId, EnvironmentTarget target,
            List<String> selectedToolKeys, List<PermissionSelection> permissionSelections,
            ExistingState existingState, List<PreviewInputFile> providedFiles, String generatorVersion) {}
}
