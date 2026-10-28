package com.agentfit.coreapi.catalog.discovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SkillsShCandidateExportControllerTest {
    @TempDir Path directory;
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);

    @Test
    void authenticatedUserCanDownloadPinnedCandidateZip() throws Exception {
        Path snapshot = snapshot();
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        var controller = new SkillsShCandidateExportController(principals, snapshot.toString(),
            candidate -> new SkillsShCandidateCollector.SkillPackage(candidate.contentHash(),
                Map.of("SKILL.md", "# Example")));
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(post("/api/tool-candidates/export")
                .contentType("application/json")
                .content("{\"externalId\":\"owner/repo/example\",\"contentHash\":\"" + "a".repeat(64) + "\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/zip"))
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void anonymousUserCannotExport() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.empty());
        var controller = new SkillsShCandidateExportController(principals, snapshot().toString(),
            candidate -> { throw new AssertionError("must not download"); });
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(post("/api/tool-candidates/export").contentType("application/json")
                .content("{\"externalId\":\"owner/repo/example\",\"contentHash\":\"" + "a".repeat(64) + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void changedRemoteSkillReturnsConflict() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        var controller = new SkillsShCandidateExportController(principals, snapshot().toString(),
            candidate -> { throw new SkillsShCandidateCollector.DiscoveryException(
                "skills.sh content changed since discovery"); });
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(post("/api/tool-candidates/export").contentType("application/json")
                .content("{\"externalId\":\"owner/repo/example\",\"contentHash\":\"" + "a".repeat(64) + "\"}"))
            .andExpect(status().isConflict());
    }

    private Path snapshot() {
        Path path = directory.resolve("candidates.json");
        SkillsShCandidateSnapshot.refresh(path, List.of(new SkillCandidate(
            "owner/repo/example", "Example", "owner/repo",
            "https://skills.sh/owner/repo/example", "a".repeat(64),
            Instant.parse("2026-10-28T00:00:00Z"))));
        return path;
    }
}
