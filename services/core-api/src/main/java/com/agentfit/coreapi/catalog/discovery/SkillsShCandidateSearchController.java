package com.agentfit.coreapi.catalog.discovery;

import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bounded live discovery. Results are pinned candidates, not verified Catalog tools. */
@RestController
public final class SkillsShCandidateSearchController {
    public record SearchResponse(String source, String verificationStatus,
                                 List<SkillCandidate> candidates) {}

    private final SessionPrincipalResolver principals;
    private final SkillsShCandidateCollector collector;

    @Autowired
    public SkillsShCandidateSearchController(SessionPrincipalResolver principals) {
        this(principals, SkillsShCandidateCollector.publicExperimental());
    }

    SkillsShCandidateSearchController(SessionPrincipalResolver principals,
                                      SkillsShCandidateCollector collector) {
        this.principals = principals;
        this.collector = collector;
    }

    @GetMapping("/api/tool-candidates/search")
    public ResponseEntity<SearchResponse> search(HttpServletRequest request,
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        List<SkillCandidate> candidates = collector.search(query, limit);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new SearchResponse("skills.sh", "UNVERIFIED", candidates));
    }

    @ExceptionHandler(SkillsShCandidateCollector.DiscoveryException.class)
    public ResponseEntity<Map<String, String>> sourceUnavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).cacheControl(CacheControl.noStore())
            .body(Map.of("code", "SKILL_SOURCE_UNAVAILABLE"));
    }
}
