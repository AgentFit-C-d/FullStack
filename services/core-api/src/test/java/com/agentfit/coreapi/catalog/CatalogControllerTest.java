package com.agentfit.coreapi.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogControllerTest {
    @TempDir Path directory;
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs =
        mock(ObjectProvider.class);

    @Test
    void authenticatedUserReadsOnlyApprovedFilteredCatalog() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        when(catalogs.getIfAvailable()).thenReturn(new ServerConfiguredCatalogReader(directory, hash));

        mvc().perform(get("/api/tool-catalog").param("osFamily", "WINDOWS")
                .param("clientId", "example-client")
                .param("clientVersion", "1.0")
                .param("capabilityId", "cap_document_reference"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision").value("synthetic-recommendation"))
            .andExpect(jsonPath("$.catalogHash").value(hash))
            .andExpect(jsonPath("$.items[0].key").value("example-tool"))
            .andExpect(jsonPath("$.items[0].support[0].verified").value(true));

        mvc().perform(get("/api/tool-catalog").param("clientVersion", "other-version"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void unauthenticatedRequestCannotReadCatalog() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.empty());
        mvc().perform(get("/api/tool-catalog"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(catalogs);
    }

    @Test
    void rejectsUnexpectedAndRepeatedFilters() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        mvc().perform(get("/api/tool-catalog").param("unknown", "x"))
            .andExpect(status().isUnprocessableEntity());
        mvc().perform(get("/api/tool-catalog").param("clientId", "one", "two"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(catalogs);
    }

    @Test
    void missingOrUnapprovedCatalogReturns503() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        mvc().perform(get("/api/tool-catalog"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
        SyntheticCatalogBundle.write(directory);
        when(catalogs.getIfAvailable()).thenReturn(new ServerConfiguredCatalogReader(
            directory, "b".repeat(64)));
        mvc().perform(get("/api/tool-catalog"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new CatalogController(principals, catalogs))
            .setControllerAdvice(new GlobalExceptionHandler(), new CatalogControllerExceptionHandler())
            .build();
    }
}
