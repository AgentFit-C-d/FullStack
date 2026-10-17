package com.agentfit.coreapi.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.catalog.CatalogPreviewRequest;
import com.agentfit.coreapi.configuration.api.ConfigurationDownloadController;
import com.agentfit.coreapi.configuration.api.ConfigurationDownloadRuntimeService;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConfigurationDownloadControllerTest {
    @Test
    void downloadedZipHasNoStoreContentAndIsOwnerChecked() throws Exception {
        var principals = mock(SessionPrincipalResolver.class);
        var ownership = mock(ProjectOwnershipGuard.class);
        var downloads = mock(ConfigurationDownloadRuntimeService.class);
        var principal = mock(AuthenticatedPrincipal.class);
        UUID actorId = UUID.randomUUID();
        when(principal.userUuid()).thenReturn(actorId);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(downloads.download(eq("project-1"), eq("approval-1"), eq("preview-1"),
            eq("abc"), any(CatalogPreviewRequest.class), eq(actorId)))
            .thenReturn(new ConfigurationDownloadRuntimeService.Result("export-1",
                new byte[]{1, 2}, java.util.List.of("cap_browser_verification")));

        MockMvcBuilders.standaloneSetup(new ConfigurationDownloadController(
                principals, ownership, downloads)).build().perform(post(
                "/api/projects/project-1/config-approvals/approval-1/download")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"previewId\":\"preview-1\",\"fingerprint\":\"abc\",\"previewRequest\":{}}"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Config-Export-Id", "export-1"))
            .andExpect(header().string("X-Unmet-Capability-Keys", "cap_browser_verification"))
            .andExpect(header().string("Content-Type", "application/zip"));
        verify(ownership).requireOwned(principal, "project-1");
    }
}
