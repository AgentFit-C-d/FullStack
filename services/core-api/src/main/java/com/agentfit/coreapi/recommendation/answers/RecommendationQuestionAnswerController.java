package com.agentfit.coreapi.recommendation.answers;

import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/recommendations/{recommendationId}/answers")
public class RecommendationQuestionAnswerController {
    public record AnswerRequest(String answer) {}
    public record AnswerList(List<RecommendationQuestionAnswer> items) {}

    private final SessionPrincipalResolver principals;
    private final ProjectOwnershipGuard ownership;
    private final RecommendationQuestionAnswerService answers;

    public RecommendationQuestionAnswerController(SessionPrincipalResolver principals,
            ProjectOwnershipGuard ownership, RecommendationQuestionAnswerService answers) {
        this.principals = principals;
        this.ownership = ownership;
        this.answers = answers;
    }

    @GetMapping
    public ResponseEntity<AnswerList> list(HttpServletRequest request, @PathVariable String projectId,
                                            @PathVariable String recommendationId) {
        var principal = principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        ownership.requireOwned(principal, projectId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new AnswerList(answers.list(projectId, recommendationId)));
    }

    @PutMapping("/{noticeId}")
    public ResponseEntity<RecommendationQuestionAnswer> save(HttpServletRequest request,
            @PathVariable String projectId, @PathVariable String recommendationId,
            @PathVariable String noticeId, @RequestBody AnswerRequest body) {
        var principal = principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        ownership.requireOwned(principal, projectId);
        if (body == null) throw new IllegalArgumentException("answer is required");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(answers.save(
            projectId, recommendationId, noticeId, body.answer(), principal.userUuid()));
    }
}
