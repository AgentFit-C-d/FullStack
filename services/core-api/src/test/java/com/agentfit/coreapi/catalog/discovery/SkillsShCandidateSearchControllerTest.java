package com.agentfit.coreapi.catalog.discovery;

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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SkillsShCandidateSearchControllerTest {
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
    private final SkillsShCandidateCollector.Transport transport = mock(SkillsShCandidateCollector.Transport.class);

    @Test
    void authenticatedSearchReturnsPinnedRemoteCandidates() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        when(transport.get(any(), any())).thenAnswer(call -> {
            String path = call.getArgument(0, URI.class).getPath();
            String json = path.equals("/api/search")
                ? "{\"skills\":[{\"id\":\"owner/repo/test-skill\",\"name\":\"test-skill\",\"source\":\"owner/repo\"}]}"
                : "{\"hash\":\"" + "a".repeat(64) + "\"}";
            return new SkillsShCandidateCollector.Response(200, json.getBytes(StandardCharsets.UTF_8));
        });
        mvc().perform(get("/api/tool-candidates/search").param("q", "test").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidates[0].externalId").value("owner/repo/test-skill"))
            .andExpect(jsonPath("$.candidates[0].contentHash").value("a".repeat(64)));
    }

    @Test
    void anonymousSearchNeverCallsRemoteSource() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.empty());
        mvc().perform(get("/api/tool-candidates/search").param("q", "test"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(transport);
    }

    @Test
    void invalidQueryIsRejectedBeforeRemoteCall() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        mvc().perform(get("/api/tool-candidates/search").param("q", "x"))
            .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(transport);
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        var collector = SkillsShCandidateCollector.publicExperimental(transport,
            URI.create("https://skills.sh"), Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC));
        return MockMvcBuilders.standaloneSetup(new SkillsShCandidateSearchController(principals, collector))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
