package com.agentfit.coreapi.catalog.discovery;

import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Project-scoped related Skill candidates; compatibility and combination remain unconfirmed. */
@RestController
public final class SkillCandidateSuggestionController {
    private final SessionPrincipalResolver principals;
    private final ProjectOwnershipGuard ownership;
    private final ObjectProvider<RecommendationLatestDetailsService> latest;
    private final String snapshotPath;

    public SkillCandidateSuggestionController(SessionPrincipalResolver principals,
            ProjectOwnershipGuard ownership, ObjectProvider<RecommendationLatestDetailsService> latest,
            @Value("${agentfit.skills-sh.snapshot-path:}") String snapshotPath) {
        this.principals = principals;
        this.ownership = ownership;
        this.latest = latest;
        this.snapshotPath = snapshotPath;
    }

    @GetMapping("/api/projects/{projectId}/skill-candidate-suggestions")
    public ResponseEntity<SkillCandidateSuggestionMatcher.Result> list(
            HttpServletRequest request, @PathVariable String projectId) {
        AuthenticatedPrincipal principal = principals.resolve(request)
            .orElseThrow(UnauthenticatedException::new);
        ownership.requireOwned(principal, projectId);
        var service = latest.getIfAvailable();
        if (service == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        var recommendation = service.latest(projectId);
        if (recommendation == null) return ResponseEntity.notFound().build();
        if (recommendation.summary().validity() != RecommendationReadModel.Validity.CURRENT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        if (snapshotPath == null || snapshotPath.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        Path snapshot = Path.of(snapshotPath);
        if (!Files.isRegularFile(snapshot, LinkOption.NOFOLLOW_LINKS)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        Map<JdbcRecommendationMetadataWriter.Necessity, Set<String>> byNecessity =
            recommendation.capabilities().stream().collect(Collectors.groupingBy(
                JdbcRecommendationMetadataWriter.Capability::necessity,
                Collectors.mapping(JdbcRecommendationMetadataWriter.Capability::key,
                    Collectors.toUnmodifiableSet())));
        var result = SkillCandidateSuggestionMatcher.suggest(SkillsShCandidateSnapshot.load(snapshot),
            byNecessity.getOrDefault(JdbcRecommendationMetadataWriter.Necessity.REQUIRED, Set.of()),
            byNecessity.getOrDefault(JdbcRecommendationMetadataWriter.Necessity.OPTIONAL, Set.of()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @ExceptionHandler(SkillsShCandidateSnapshot.InvalidSnapshotException.class)
    public ResponseEntity<Map<String, String>> invalidSnapshot() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("code", "SKILL_CANDIDATES_UNAVAILABLE"));
    }
}
