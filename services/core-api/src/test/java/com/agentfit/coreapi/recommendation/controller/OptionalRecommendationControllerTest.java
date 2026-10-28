package com.agentfit.coreapi.recommendation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.catalog.CatalogRecommendationWorkflow;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OptionalRecommendationControllerTest {
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
    private final ProjectOwnershipGuard ownership = mock(ProjectOwnershipGuard.class);
    private final RecommendationCreationService service = mock(RecommendationCreationService.class);
    private final ObjectProvider<RecommendationCreationService> provider = mock(ObjectProvider.class);

    @Test
    void ownedUserSelectionCreatesNewSavedRecommendation() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(provider.getIfAvailable()).thenReturn(service);
        var basis = new PreviewBasis("project-1", 7, "profile-1", 3,
            "event-1", 2, 4, 5, "catalog-1", "a".repeat(64));
        var result = new CatalogRecommendationWorkflow.Result("catalog-1", "a".repeat(64),
            new RecommendationDecision(RecommendationDecision.Status.NO_COMPATIBLE_TOOLS,
                List.of(), List.of(), List.of()), List.of(), List.of(), List.of());
        when(service.create(eq("project-1"), any(), eq(Set.of("cap_test_execution"))))
            .thenReturn(new RecommendationCreationService.Created("recommendation-1", basis, result));

        mvc().perform(post("/api/projects/project-1/recommendations/optional")
            .contentType("application/json")
            .content("{\"expectedVersions\":{\"project\":7,\"developer\":4,\"environment\":5},"
                + "\"optionalCapabilityKeys\":[\"cap_test_execution\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.recommendationId").value("recommendation-1"));
        verify(ownership).requireOwned(principal, "project-1");
        verify(service).create(eq("project-1"), any(), eq(Set.of("cap_test_execution")));
    }

    @Test
    void anonymousUserCannotCreateOptionalRecommendation() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.empty());
        mvc().perform(post("/api/projects/project-1/recommendations/optional")
            .contentType("application/json")
            .content("{\"expectedVersions\":{\"project\":7,\"developer\":4,\"environment\":5},"
                + "\"optionalCapabilityKeys\":[\"cap_test_execution\"]}"))
            .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new OptionalRecommendationController(
            principals, ownership, provider)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
