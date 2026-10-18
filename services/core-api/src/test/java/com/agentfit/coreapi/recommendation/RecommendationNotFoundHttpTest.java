package com.agentfit.coreapi.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import com.agentfit.coreapi.recommendation.controller.RecommendationController;
import com.agentfit.coreapi.recommendation.controller.RecommendationResponseStatusHandler;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendationNotFoundHttpTest {
    @Test
    void authenticatedProjectWithoutRecommendationReturns404() throws Exception {
        SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        @SuppressWarnings("unchecked")
        ObjectProvider<RecommendationLatestDetailsService> latestProvider = mock(ObjectProvider.class);
        RecommendationLatestDetailsService latest = mock(RecommendationLatestDetailsService.class);
        when(latestProvider.getIfAvailable()).thenReturn(latest);
        var controller = new RecommendationController(principals, mock(ProjectOwnershipGuard.class),
            mock(ObjectProvider.class), latestProvider, mock(ObjectProvider.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RecommendationResponseStatusHandler(), new GlobalExceptionHandler())
            .build();

        mvc.perform(get("/api/projects/project-1/recommendations/latest"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RECOMMENDATION_NOT_FOUND"));
    }
}
