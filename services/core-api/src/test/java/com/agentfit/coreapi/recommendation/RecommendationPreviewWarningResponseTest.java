package com.agentfit.coreapi.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentfit.coreapi.catalog.PreviewCapabilityCoverage;
import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.recommendation.controller.RecommendationController;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

class RecommendationPreviewWarningResponseTest {
    @Test
    void partialPreviewReturnsUnmetCapabilitiesBeforeApproval() {
        SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
        ProjectOwnershipGuard ownership = mock(ProjectOwnershipGuard.class);
        PreviewCreationService previews = mock(PreviewCreationService.class);
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        PreviewFile file = new PreviewFile("claude-code", ".claude/settings.json",
            PreviewFile.Action.PROPOSAL, null, "after-hash", "{\"allowed\":true}",
            null, PreviewFile.ComparisonScope.EXISTING_UNKNOWN);
        when(previews.create(any(), any())).thenReturn(new PreviewCreationService.Created(
            new StoredPreviewState("preview-1", null, "fingerprint",
                Instant.parse("2026-10-18T00:00:00Z"), Instant.parse("2026-10-18T00:30:00Z")),
            new PreviewFingerprintResult(List.of(file), "selection", "content", "fingerprint"),
            new PreviewCapabilityCoverage.Result(List.of("cap_browser_verification"))));
        @SuppressWarnings("unchecked")
        ObjectProvider<PreviewCreationService> previewProvider = mock(ObjectProvider.class);
        when(previewProvider.getIfAvailable()).thenReturn(previews);
        RecommendationController controller = new RecommendationController(principals, ownership,
            mock(ObjectProvider.class), mock(ObjectProvider.class), previewProvider);

        var response = controller.createPreview(new MockHttpServletRequest(), "project-1",
            new RecommendationController.PreviewRequest(null, "recommendation-1", null,
                List.of(), List.of(), null, List.of(), "static-v1"));

        assertEquals(List.of("cap_browser_verification"), response.getBody().unmetCapabilityKeys());
        assertEquals(List.of(file), response.getBody().files());
        assertEquals("no-store", response.getHeaders().getCacheControl());
    }
}
