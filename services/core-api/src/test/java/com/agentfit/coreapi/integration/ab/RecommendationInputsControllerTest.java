package com.agentfit.coreapi.integration.ab;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendationInputsControllerTest {
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
    private final ProjectOwnershipGuard ownership = mock(ProjectOwnershipGuard.class);
    private final RecommendationInputsService inputs = mock(RecommendationInputsService.class);

    @Test
    void ownerCanReadSavedInputVersionsForRecommendationRequest() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(inputs.current("project-1")).thenReturn(new RecommendationInputsService.Current(
            new RecommendationInputsService.Developer(1, "FULL_STACK", java.util.List.of("API 검증")),
            new RecommendationInputsService.Environment(2, "WINDOWS", "example-client", "1.0",
                true, java.util.List.of())));

        mvc().perform(get("/api/projects/project-1/recommendation-inputs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.developer.version").value(1))
            .andExpect(jsonPath("$.environment.version").value(2));
        verify(ownership).requireOwned(principal, "project-1");
    }

    @Test
    void ownerCanSaveEnvironmentWithExplicitlyKnownEmptyInstallList() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(inputs.saveEnvironment(eq("project-1"), eq(0L), eq("WINDOWS"),
            eq("example-client"), eq("1.0"), eq(true), eq(java.util.List.of())))
            .thenReturn(1L);

        mvc().perform(put("/api/projects/project-1/environment-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"osFamily\":\"WINDOWS\",\"clientId\":\"example-client\",\"clientVersion\":\"1.0\",\"installedComponentsKnown\":true,\"installedComponents\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));
        verify(ownership).requireOwned(principal, "project-1");
    }

    @Test
    void otherUsersProjectCannotSaveDeveloperInput() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        doThrow(new ProjectNotFoundException()).when(ownership).requireOwned(principal, "foreign");

        mvc().perform(put("/api/projects/foreign/developer-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"role\":\"FULL_STACK\",\"responsibilities\":[]}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(inputs);
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new RecommendationInputsController(principals, ownership, inputs))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
