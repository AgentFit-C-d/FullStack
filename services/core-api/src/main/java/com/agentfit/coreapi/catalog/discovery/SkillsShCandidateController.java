package com.agentfit.coreapi.catalog.discovery;

import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only research candidates. This route never exposes an approved Catalog release. */
@RestController
public final class SkillsShCandidateController {
    public record CandidateResponse(String source, String verificationStatus,
                                    List<SkillCandidate> candidates) {}

    private final SessionPrincipalResolver principals;
    private final String snapshotPath;

    public SkillsShCandidateController(SessionPrincipalResolver principals,
            @Value("${agentfit.skills-sh.snapshot-path:}") String snapshotPath) {
        this.principals = principals;
        this.snapshotPath = snapshotPath;
    }

    @GetMapping("/api/tool-candidates")
    public ResponseEntity<CandidateResponse> list(HttpServletRequest request) {
        principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        if (snapshotPath == null || snapshotPath.isBlank()) {
            return unavailable();
        }
        Path path = Path.of(snapshotPath);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return unavailable();
        }
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CandidateResponse("skills.sh", "UNVERIFIED",
                    SkillsShCandidateSnapshot.load(path)));
        } catch (SkillsShCandidateSnapshot.InvalidSnapshotException exception) {
            return unavailable();
        }
    }

    private static ResponseEntity<CandidateResponse> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .cacheControl(CacheControl.noStore()).build();
    }
}
