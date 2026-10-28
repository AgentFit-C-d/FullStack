package com.agentfit.coreapi.recommendation.controller;

import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** B-owned explicit optional interest route; the existing A recommendation API is unchanged. */
@RestController
public final class OptionalRecommendationController {
    public record OptionalRequest(RecommendationController.ExpectedVersions expectedVersions,
                                  Set<String> optionalCapabilityKeys) {}

    private final SessionPrincipalResolver principals;
    private final ProjectOwnershipGuard ownership;
    private final ObjectProvider<RecommendationCreationService> recommendations;

    public OptionalRecommendationController(SessionPrincipalResolver principals,
            ProjectOwnershipGuard ownership,
            ObjectProvider<RecommendationCreationService> recommendations) {
        this.principals = principals;
        this.ownership = ownership;
        this.recommendations = recommendations;
    }

    @PostMapping("/api/projects/{projectId}/recommendations/optional")
    public ResponseEntity<RecommendationController.RecommendationCreatedResponse> create(
            HttpServletRequest request, @PathVariable String projectId,
            @RequestBody OptionalRequest body) {
        var principal = principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        ownership.requireOwned(principal, projectId);
        if (body == null || body.expectedVersions() == null || body.optionalCapabilityKeys() == null) {
            throw new IllegalArgumentException("optional recommendation request required");
        }
        var service = recommendations.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "recommendation workflow is unavailable");
        }
        var created = service.create(projectId, new RecommendationCreationService.ExpectedVersions(
            body.expectedVersions().project(), body.expectedVersions().developer(),
            body.expectedVersions().environment()), body.optionalCapabilityKeys());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(RecommendationController.recommendationResponse(created));
    }
}
