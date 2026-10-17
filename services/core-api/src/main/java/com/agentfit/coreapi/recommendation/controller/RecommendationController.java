package com.agentfit.coreapi.recommendation.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agentfit.coreapi.catalog.CatalogPreviewRequest;
import com.agentfit.coreapi.catalog.CatalogRecommendationDetails;
import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
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
@Tag(name = "Recommendations", description = "Recommendation generation, retrieval, and configuration previews")
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
    @ApiResponse(responseCode = "201", description = "Recommendation created",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = RecommendationCreatedResponse.class)))
    public ResponseEntity<RecommendationCreatedResponse> createRecommendation(HttpServletRequest request,
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
    @ApiResponse(responseCode = "200", description = "Latest recommendation",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = RecommendationLatestResponse.class)))
    public RecommendationLatestResponse latest(HttpServletRequest request, @PathVariable String projectId) {
        AuthenticatedPrincipal principal = principal(request);
        ownership.requireOwned(principal, projectId);
        var snapshot = required(latest, "latest recommendation workflow").latest(projectId);
        if (snapshot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "recommendation not found");
        return new RecommendationLatestResponse(snapshot.summary().recommendationId(),
            snapshot.summary().status(), snapshot.summary().validity(), snapshot.summary().basis(),
            snapshot.catalogItems(), snapshot.capabilities(), snapshot.notices());
    }

    @PostMapping("/config-previews")
    @ApiResponse(responseCode = "201", description = "Configuration preview created",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = ConfigurationPreviewCreatedResponse.class)))
    public ResponseEntity<ConfigurationPreviewCreatedResponse> createPreview(HttpServletRequest request,
            @PathVariable String projectId, @RequestBody PreviewRequest body) {
        AuthenticatedPrincipal principal = principal(request);
        ownership.requireOwned(principal, projectId);
        var created = required(previews, "Preview workflow").create(projectId,
            new CatalogPreviewRequest(body.basis(), body.recommendationId(), body.target(),
                body.selectedToolKeys(), body.permissionSelections(), body.existingState(),
                body.providedFiles(), body.generatorVersion()));
        var saved = created.saved();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(new ConfigurationPreviewCreatedResponse(
            saved.previewId(), body.recommendationId(), saved.fingerprint(), created.preview().selectionDigest(),
            created.preview().contentDigest(), saved.createdAt(), saved.expiresAt(),
            created.coverage().unmetCapabilityKeys(), created.preview().files()));
    }

    private AuthenticatedPrincipal principal(HttpServletRequest request) {
        return principals.resolve(request).orElseThrow(UnauthenticatedException::new);
    }

    private static <T> T required(ObjectProvider<T> provider, String name) {
        T value = provider.getIfAvailable();
        if (value == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, name + " is unavailable");
        return value;
    }

    private static RecommendationCreatedResponse recommendationResponse(RecommendationCreationService.Created created) {
        var result = created.result();
        return new RecommendationCreatedResponse(created.recommendationId(), result.decision().status(),
            created.basis(), result.capabilities(), result.items(), result.clarificationQuestions());
    }

    public record ExpectedVersions(long project, long developer, long environment) {}
    public record RecommendationRequest(ExpectedVersions expectedVersions) {}

    public record PreviewRequest(PreviewBasis basis, String recommendationId, EnvironmentTarget target,
            List<String> selectedToolKeys, List<PermissionSelection> permissionSelections,
            ExistingState existingState, List<PreviewInputFile> providedFiles, String generatorVersion) {}

    public record RecommendationCreatedResponse(String recommendationId, RecommendationDecision.Status status,
            PreviewBasis basis, List<AiCapabilityIntake.Claim> capabilities,
            List<CatalogRecommendationDetails.Item> items, List<AiCapabilityIntake.Question> notices) {}

    public record RecommendationLatestResponse(String recommendationId, RecommendationDecision.Status status,
            RecommendationReadModel.Validity validity, PreviewBasis basis,
            List<CatalogRecommendationDetails.Item> items,
            List<JdbcRecommendationMetadataWriter.Capability> capabilities,
            List<JdbcRecommendationMetadataWriter.Notice> notices) {}

    public record ConfigurationPreviewCreatedResponse(String previewId, String recommendationId,
            String fingerprint, String selectionDigest, String contentDigest, Instant createdAt,
            Instant expiresAt, List<String> unmetCapabilityKeys, List<PreviewFile> files) {}
}
