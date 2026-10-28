package com.agentfit.coreapi.domain.analysis;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisResult;
import com.agentfit.coreapi.domain.analysis.service.ProjectAnalysisService;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.agentfit.coreapi.domain.analysis.controller.AnalysisController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.agentfit.coreapi.global.exception.GlobalExceptionHandler.class)
class ProjectAnalysisWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean ProjectService projectService;
    @MockBean ProjectAnalysisService analysisService;
    @MockBean com.agentfit.coreapi.domain.analysis.service.AnalysisReviewService reviewService;
    @MockBean SessionPrincipalResolver principalResolver;

    @Test void acceptedResponseContainsSafeProcessingMetadata() throws Exception {
        var principal = new AuthenticatedPrincipal(7, "Alice", null);
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(principal));
        var attempt = new AnalysisAttempt(1, "p1", "r1", 1, "1.0", "PROCESSING", Instant.parse("2026-10-13T00:10:00Z"), Instant.parse("2026-10-13T00:00:00Z"), null, 7L, 1L, null);
        when(analysisService.start(principal, "p1", 1)).thenReturn(attempt);

        mockMvc.perform(post("/api/projects/p1/analysis-attempts").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.state").value("PROCESSING"))
            .andExpect(jsonPath("$.projectId").value("p1"))
            .andExpect(jsonPath("$.ownerId").doesNotExist())
            .andExpect(jsonPath("$.rawContent").doesNotExist());
    }

    @Test void duplicateProjectReturnsConflictMessage() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(new AuthenticatedPrincipal(7, "Alice", null)));
        when(analysisService.start(any(), eq("p1"), eq(1L))).thenThrow(new com.agentfit.coreapi.domain.project.exception.AnalysisAlreadyProcessingException());

        mockMvc.perform(post("/api/projects/p1/analysis-attempts").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ANALYSIS_ALREADY_PROCESSING"))
            .andExpect(jsonPath("$.message").value("분석이 이미 진행 중입니다. 완료될 때까지 기다려 주세요."));
    }

    @Test void capacityReturnsTooManyRequestsEnvelope() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(new AuthenticatedPrincipal(7, "Alice", null)));
        when(analysisService.start(any(), eq("p1"), eq(1L))).thenThrow(new com.agentfit.coreapi.domain.project.exception.AnalysisCapacityExceededException(com.agentfit.coreapi.global.exception.ErrorCode.ANALYSIS_SERVER_LIMIT_REACHED));

        mockMvc.perform(post("/api/projects/p1/analysis-attempts").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("ANALYSIS_SERVER_LIMIT_REACHED"));
    }

    @Test void latestAttemptCanBeQueriedThroughAnalysisController() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(new AuthenticatedPrincipal(7, "Alice", null)));
        var attempt = new AnalysisAttempt(1, "p1", "r1", 1, "1.0", "SUCCEEDED",
                Instant.parse("2026-10-13T00:10:00Z"), Instant.parse("2026-10-13T00:00:00Z"),
                Instant.parse("2026-10-13T00:01:00Z"), 7L, 1L, null);
        when(analysisService.latest(any(), eq("p1"))).thenReturn(attempt);

        mockMvc.perform(get("/api/projects/p1/analysis-attempts/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("SUCCEEDED"))
            .andExpect(jsonPath("$.projectId").value("p1"));
    }

    @Test void latestResultCanBeQueriedThroughAnalysisController() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(new AuthenticatedPrincipal(7, "Alice", null)));
        var result = new AnalysisResult("11111111-1111-1111-1111-111111111111", "r1", 1, "1.0", Map.of(
                "profile", Map.of("features", java.util.List.of("프로젝트 분석")),
                "fieldStates", Map.of("features", "suggested"),
                "candidates", java.util.List.of()), Instant.parse("2026-10-13T00:01:00Z"));
        when(analysisService.latestResult(any(), eq("p1"))).thenReturn(result);

        mockMvc.perform(get("/api/projects/p1/analysis-results/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attemptId").value("11111111-1111-1111-1111-111111111111"))
            .andExpect(jsonPath("$.result.profile.features[0]").value("프로젝트 분석"));
    }

    @Test void reviewCanBeSavedThroughAnalysisController() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(new AuthenticatedPrincipal(7, "Alice", null)));
        when(reviewService.save(any(), eq("p1"), any())).thenReturn(Map.of("confirmedCandidateIds", java.util.List.of("c1")));

        mockMvc.perform(put("/api/projects/p1/analysis-results/latest/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedCandidateIds\":[\"c1\"],\"removedCandidateIds\":[],\"manualCandidates\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmedCandidateIds[0]").value("c1"));
    }
}
