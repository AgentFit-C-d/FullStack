package com.agentfit.coreapi.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.configuration.api.ConfigurationFinalizationExceptionHandler;
import com.agentfit.coreapi.catalog.CatalogBundleLoader;
import com.agentfit.coreapi.configuration.api.ConfigurationHistoryController;
import com.agentfit.coreapi.configuration.api.ConfigurationHistoryRuntimeService;
import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryPageService;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConfigurationHistoryControllerTest {
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
    private final ProjectOwnershipGuard ownership = mock(ProjectOwnershipGuard.class);
    private final ConfigurationHistoryRuntimeService history = mock(ConfigurationHistoryRuntimeService.class);
    private final String project = UUID.randomUUID().toString();

    @Test
    void ownerReadsContentFreeCurrentHistory() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        var basis = new PreviewBasis(project, 1, UUID.randomUUID().toString(), 1,
            UUID.randomUUID().toString(), 0, 1, 1, "release-1", "a".repeat(64));
        var generation = ConfigurationGenerationHistory.fromStoredMetadata(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), basis, "b".repeat(64),
            List.of(new ConfigurationGenerationHistory.FileMetadata("config.json",
                PreviewFile.Action.PROPOSAL, "c".repeat(64))), Instant.parse("2026-10-16T00:00:00Z"));
        when(history.list(eq(project), eq(null), eq(20))).thenReturn(
            new ConfigurationHistoryPageService.Page(
                List.of(ConfigurationHistoryReadModel.project(generation, basis, null)), null));

        mvc().perform(get("/api/projects/{projectId}/configuration-history", project))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(generation.id()))
            .andExpect(jsonPath("$.items[0].validity").value("CURRENT"))
            .andExpect(jsonPath("$.items[0].verification.status").value("NOT_RUN"))
            .andExpect(jsonPath("$.items[0].verification.source").value("NONE"))
            .andExpect(jsonPath("$.items[0].files[0].relativePath").value("config.json"))
            .andExpect(jsonPath("$.items[0].content").doesNotExist());
    }

    @Test
    void foreignProjectCannotReadHistory() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        doThrow(new ProjectNotFoundException()).when(ownership).requireOwned(principal, project);
        mvc().perform(get("/api/projects/{projectId}/configuration-history", project))
            .andExpect(status().isNotFound());
        verifyNoInteractions(history);
    }

    @Test
    void malformedCursorIsRejectedBeforeRead() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        mvc().perform(get("/api/projects/{projectId}/configuration-history", project)
                .param("cursor", "bad-cursor"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(history);
    }

    @Test
    void invalidLimitIsRejectedBeforeRead() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        mvc().perform(get("/api/projects/{projectId}/configuration-history", project)
                .param("limit", "101"))
            .andExpect(status().isUnprocessableEntity());
        mvc().perform(get("/api/projects/{projectId}/configuration-history", project)
                .param("limit", "many"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(history);
    }

    @Test
    void unavailableApprovedCatalogReturnsRetryableServiceError() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(history.list(eq(project), eq(null), eq(20)))
            .thenThrow(new CatalogBundleLoader.CatalogUnavailableException("hash mismatch"));

        mvc().perform(get("/api/projects/{projectId}/configuration-history", project))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new ConfigurationHistoryController(
            principals, ownership, history)).setControllerAdvice(new GlobalExceptionHandler(),
                new ConfigurationFinalizationExceptionHandler()).build();
    }
}
