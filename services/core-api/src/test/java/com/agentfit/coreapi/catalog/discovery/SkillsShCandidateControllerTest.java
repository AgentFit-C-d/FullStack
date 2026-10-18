package com.agentfit.coreapi.catalog.discovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SkillsShCandidateControllerTest {
    @TempDir Path directory;
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);

    @Test
    void authenticatedUserSeesStagedCandidatesOnlyAsUnverified() throws Exception {
        Path snapshot = directory.resolve("skills-sh-candidates.json");
        SkillsShCandidateSnapshot.refresh(snapshot, List.of(new SkillCandidate(
            "vercel-labs/agent-skills/vercel-react-best-practices",
            "vercel-react-best-practices", "vercel-labs/agent-skills",
            "https://skills.sh/vercel-labs/agent-skills/vercel-react-best-practices",
            "a".repeat(64), Instant.parse("2026-10-18T00:00:00Z"))));
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));

        mvc(snapshot).perform(get("/api/tool-candidates"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.source").value("skills.sh"))
            .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
            .andExpect(jsonPath("$.candidates[0].externalId")
                .value("vercel-labs/agent-skills/vercel-react-best-practices"));
    }

    @Test
    void missingSnapshotFailsInsteadOfPretendingThereAreNoCandidates() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        mvc(directory.resolve("missing.json")).perform(get("/api/tool-candidates"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void anonymousUserCannotReadCandidates() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.empty());
        mvc(directory.resolve("missing.json")).perform(get("/api/tool-candidates"))
            .andExpect(status().isUnauthorized());
    }

    private MockMvc mvc(Path snapshot) {
        return MockMvcBuilders.standaloneSetup(new SkillsShCandidateController(principals, snapshot.toString()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
