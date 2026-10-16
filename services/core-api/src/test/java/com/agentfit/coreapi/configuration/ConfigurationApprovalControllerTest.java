package com.agentfit.coreapi.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.configuration.api.ConfigurationApprovalController;
import com.agentfit.coreapi.configuration.api.ConfigurationApprovalService;
import com.agentfit.coreapi.configuration.api.ConfigurationFinalizationExceptionHandler;
import com.agentfit.coreapi.configuration.export.PreviewFreshnessGate;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ConfigurationApprovalControllerTest {
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
    private final ProjectOwnershipGuard ownership = mock(ProjectOwnershipGuard.class);
    private final ConfigurationApprovalService approvals = mock(ConfigurationApprovalService.class);
    private final ConfigurationApprovalController controller =
        new ConfigurationApprovalController(principals, ownership, approvals);

    @Test
    void approvalUsesAuthenticatedProjectOwnerAndReturnsWarnings() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principal.userUuid()).thenReturn(userId);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(approvals.approve(eq("project-1"), eq("preview-1"), eq("abc"), eq(true), eq(userId)))
            .thenReturn(new ConfigurationApprovalService.Result("approval-1", "preview-1", "abc",
                Instant.parse("2026-10-16T00:00:00Z"), Instant.parse("2026-10-16T00:30:00Z"),
                List.of("TESTING")));

        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
            .build().perform(post(
                "/api/projects/project-1/config-previews/preview-1/approval")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fingerprint\":\"abc\",\"confirmed\":true}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.approvalId").value("approval-1"))
            .andExpect(jsonPath("$.unmetCapabilityKeys[0]").value("TESTING"));
        verify(ownership).requireOwned(principal, "project-1");
        verify(approvals).approve("project-1", "preview-1", "abc", true, userId);
    }

    @Test
    void foreignProjectCannotReachApprovalService() throws Exception {
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        doThrow(new ProjectNotFoundException()).when(ownership).requireOwned(principal, "foreign");
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
            .build().perform(post(
                "/api/projects/foreign/config-previews/preview-1/approval")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fingerprint\":\"abc\",\"confirmed\":true}"))
            .andExpect(status().isNotFound());
        org.mockito.Mockito.verifyNoInteractions(approvals);
    }

    @Test
    void stalePreviewIsReportedAsConflictForFrontendRefresh() throws Exception {
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principal.userUuid()).thenReturn(UUID.randomUUID());
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(approvals.approve(eq("project-1"), eq("preview-1"), eq("abc"), eq(true), any()))
            .thenThrow(new PreviewFreshnessGate.StalePreviewException("Preview basis changed"));

        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler(),
                new ConfigurationFinalizationExceptionHandler()).build().perform(post(
                "/api/projects/project-1/config-previews/preview-1/approval")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fingerprint\":\"abc\",\"confirmed\":true}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void missingPreviewIsReportedAsNotFound() throws Exception {
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principal.userUuid()).thenReturn(UUID.randomUUID());
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(approvals.approve(eq("project-1"), eq("preview-1"), eq("abc"), eq(true), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Preview not found"));

        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler(),
                new ConfigurationFinalizationExceptionHandler()).build().perform(post(
                "/api/projects/project-1/config-previews/preview-1/approval")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fingerprint\":\"abc\",\"confirmed\":true}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CONFIGURATION_NOT_FOUND"));
    }
}
