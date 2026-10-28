package com.agentfit.coreapi.domain.analysis.controller;

import com.agentfit.coreapi.domain.analysis.dto.response.AnalysisAttemptResponse;
import com.agentfit.coreapi.domain.analysis.dto.response.AnalysisResultResponse;
import com.agentfit.coreapi.domain.analysis.dto.request.AnalysisReviewRequest;
import com.agentfit.coreapi.domain.analysis.service.AnalysisReviewService;
import com.agentfit.coreapi.domain.analysis.service.ProjectAnalysisService;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.dto.request.AnalysisStartRequest;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** HTTP boundary for the analysis lifecycle of a project. */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class AnalysisController {
    private final ProjectAnalysisService analysisService;
    private final AnalysisReviewService reviewService;
    private final SessionPrincipalResolver principalResolver;

    public AnalysisController(ProjectAnalysisService analysisService,
            AnalysisReviewService reviewService,
            SessionPrincipalResolver principalResolver) {
        this.analysisService = analysisService;
        this.reviewService = reviewService;
        this.principalResolver = principalResolver;
    }

    @PostMapping("/analysis-attempts")
    public ResponseEntity<AnalysisAttemptResponse> start(HttpServletRequest request,
            @PathVariable String projectId, @RequestBody AnalysisStartRequest body) {
        var attempt = analysisService.start(principal(request), projectId, body.expectedVersion());
        return ResponseEntity.accepted().body(AnalysisAttemptResponse.from(attempt));
    }

    @GetMapping("/analysis-attempts/latest")
    public AnalysisAttemptResponse status(HttpServletRequest request,
            @PathVariable String projectId) {
        return AnalysisAttemptResponse.from(analysisService.latest(principal(request), projectId));
    }

    @GetMapping("/analysis-results/latest")
    public AnalysisResultResponse result(HttpServletRequest request,
            @PathVariable String projectId) {
        return AnalysisResultResponse.from(analysisService.latestResult(principal(request), projectId));
    }

    @PutMapping("/analysis-results/latest/review")
    public Map<String, Object> review(HttpServletRequest request,
            @PathVariable String projectId,
            @RequestBody AnalysisReviewRequest body) {
        return reviewService.save(principal(request), projectId, body);
    }

    private AuthenticatedPrincipal principal(HttpServletRequest request) {
        return principalResolver.resolve(request).orElseThrow(UnauthenticatedException::new);
    }
}
