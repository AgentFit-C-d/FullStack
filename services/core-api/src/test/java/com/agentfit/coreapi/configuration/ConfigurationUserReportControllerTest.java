package com.agentfit.coreapi.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.configuration.api.ConfigurationUserReportController;
import com.agentfit.coreapi.configuration.api.ConfigurationFinalizationExceptionHandler;
import com.agentfit.coreapi.configuration.api.ConfigurationUserReportRuntimeService;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ConfigurationUserReportControllerTest {
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
    private final ProjectOwnershipGuard ownership = mock(ProjectOwnershipGuard.class);
    private final ConfigurationUserReportRuntimeService reports =
        mock(ConfigurationUserReportRuntimeService.class);

    @Test
    void ownerCanRecordApplicationStatementWithoutClaimingVerification() throws Exception {
        UUID actor = UUID.randomUUID();
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principal.userUuid()).thenReturn(actor);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(reports.report(eq("project-1"), eq("export-1"), any(), eq(actor)))
            .thenReturn(new ConfigurationHistoryReadModel.UserReport("report-1", "export-1",
                ConfigurationUserReport.ReportedState.APPLIED, null,
                ConfigurationUserReport.Source.USER, Instant.parse("2026-10-16T00:00:00Z")));

        mvc().perform(post("/api/projects/project-1/configuration-history/export-1/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportedState\":\"APPLIED\",\"reasonCode\":null}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.report.id").value("report-1"))
            .andExpect(jsonPath("$.report.source").value("USER"))
            .andExpect(jsonPath("$.report.verification").doesNotExist());
        verify(ownership).requireOwned(principal, "project-1");
        verify(reports).report(eq("project-1"), eq("export-1"), any(), eq(actor));
    }

    @Test
    void foreignProjectCannotReachReportService() throws Exception {
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        doThrow(new ProjectNotFoundException()).when(ownership).requireOwned(principal, "foreign");

        mvc().perform(post("/api/projects/foreign/configuration-history/export-1/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportedState\":\"APPLIED\",\"reasonCode\":null}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(reports);
    }

    @Test
    void rejectsUnexpectedReportFieldsBeforePersistence() throws Exception {
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principal.userUuid()).thenReturn(UUID.randomUUID());
        when(principals.resolve(any())).thenReturn(Optional.of(principal));

        mvc().perform(post("/api/projects/project-1/configuration-history/export-1/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportedState\":\"APPLIED\",\"reasonCode\":null,"
                    + "\"verified\":true}"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(reports);
    }

    @Test
    void missingGenerationReturnsNotFound() throws Exception {
        UUID actor = UUID.randomUUID();
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principal.userUuid()).thenReturn(actor);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(reports.report(eq("project-1"), eq("missing"), any(), eq(actor)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "generation not found"));

        mvc().perform(post("/api/projects/project-1/configuration-history/missing/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reportedState\":\"APPLIED\",\"reasonCode\":null}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CONFIGURATION_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new ConfigurationUserReportController(
            principals, ownership, reports)).setControllerAdvice(new GlobalExceptionHandler(),
                new ConfigurationFinalizationExceptionHandler()).build();
    }
}
