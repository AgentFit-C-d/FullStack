package com.agentfit.coreapi.catalog.discovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import java.nio.file.Path;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SkillCandidateSuggestionControllerTest {
    @TempDir Path directory;
    private final SessionPrincipalResolver principals = mock(SessionPrincipalResolver.class);
    private final ProjectOwnershipGuard ownership = mock(ProjectOwnershipGuard.class);
    private final RecommendationLatestDetailsService latest = mock(RecommendationLatestDetailsService.class);
    private final ObjectProvider<RecommendationLatestDetailsService> latestProvider = mock(ObjectProvider.class);

    @Test
    void currentOwnedRecommendationReturnsRelatedCandidatesAndUnmatchedKeys() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(ownership.requireOwned(principal, "project-1")).thenReturn(mock(Project.class));
        when(latest.latest("project-1")).thenReturn(snapshot(RecommendationReadModel.Validity.CURRENT));

        mvc(snapshotFile()).perform(get("/api/projects/project-1/skill-candidate-suggestions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.combinationStatus").value("UNCONFIRMED"))
            .andExpect(jsonPath("$.items[0].externalId").value("owner/repo/run-tests"))
            .andExpect(jsonPath("$.items[0].relatedCapabilityKeys[0]").value("cap_test_execution"))
            .andExpect(jsonPath("$.unmatchedRequiredCapabilityKeys[0]").value("cap_database_schema_inspection"));
        verify(ownership).requireOwned(principal, "project-1");
    }

    @Test
    void staleRecommendationDoesNotOfferCurrentSuggestions() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.of(mock(AuthenticatedPrincipal.class)));
        when(latest.latest("project-1")).thenReturn(snapshot(RecommendationReadModel.Validity.STALE));
        mvc(snapshotFile()).perform(get("/api/projects/project-1/skill-candidate-suggestions"))
            .andExpect(status().isConflict());
    }

    @Test
    void anonymousUserCannotSeeProjectSuggestions() throws Exception {
        when(principals.resolve(any())).thenReturn(Optional.empty());
        mvc(snapshotFile()).perform(get("/api/projects/project-1/skill-candidate-suggestions"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void liveSearchMatchesRemoteCandidatesToOwnedProjectNeeds() throws Exception {
        var principal = mock(AuthenticatedPrincipal.class);
        when(principals.resolve(any())).thenReturn(Optional.of(principal));
        when(ownership.requireOwned(principal, "project-1")).thenReturn(mock(Project.class));
        when(latest.latest("project-1")).thenReturn(snapshot(RecommendationReadModel.Validity.CURRENT));
        SkillsShCandidateCollector.Transport remote = (uri, token) -> {
            String json = uri.getPath().equals("/api/search")
                ? "{\"skills\":[{\"id\":\"owner/repo/run-tests\",\"name\":\"run-tests\",\"source\":\"owner/repo\"}]}"
                : "{\"hash\":\"" + "b".repeat(64) + "\"}";
            return new SkillsShCandidateCollector.Response(200, json.getBytes(StandardCharsets.UTF_8));
        };
        var collector = SkillsShCandidateCollector.publicExperimental(remote,
            URI.create("https://skills.sh"), Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC));
        when(latestProvider.getIfAvailable()).thenReturn(latest);
        var controller = new SkillCandidateSuggestionController(principals, ownership,
            latestProvider, "", collector);
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(get("/api/projects/project-1/skill-candidate-suggestions/search")
                .param("q", "tests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].contentHash").value("b".repeat(64)))
            .andExpect(jsonPath("$.items[0].relatedCapabilityKeys[0]").value("cap_test_execution"));
    }

    private MockMvc mvc(Path snapshot) {
        when(latestProvider.getIfAvailable()).thenReturn(latest);
        return MockMvcBuilders.standaloneSetup(new SkillCandidateSuggestionController(
                principals, ownership, latestProvider, snapshot.toString()))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Path snapshotFile() {
        Path path = directory.resolve("candidates.json");
        SkillsShCandidateSnapshot.refresh(path, List.of(new SkillCandidate(
            "owner/repo/run-tests", "run-tests", "owner/repo",
            "https://skills.sh/owner/repo/run-tests", "a".repeat(64),
            Instant.parse("2026-10-28T00:00:00Z"))));
        return path;
    }

    private RecommendationLatestDetailsService.Snapshot snapshot(RecommendationReadModel.Validity validity) {
        return new RecommendationLatestDetailsService.Snapshot(
            new RecommendationReadModel("rec-1", RecommendationDecision.Status.RECOMMENDED,
                validity, null, List.of("known-tool")),
            JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.parse("2026-10-28T00:00:00Z"),
            List.of(
                new JdbcRecommendationMetadataWriter.Capability("cap_test_execution",
                    JdbcRecommendationMetadataWriter.Necessity.REQUIRED, "needed", List.of()),
                new JdbcRecommendationMetadataWriter.Capability("cap_database_schema_inspection",
                    JdbcRecommendationMetadataWriter.Necessity.REQUIRED, "needed", List.of())),
            List.of(), List.of(), List.of());
    }
}
