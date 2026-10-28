package com.agentfit.coreapi.catalog.discovery;

import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Explicit export of a user-selected candidate, separate from AgentFit's recommendation decision. */
@RestController
public final class SkillsShCandidateExportController {
    public record ExportRequest(String externalId, String contentHash) {}

    private final SessionPrincipalResolver principals;
    private final String snapshotPath;
    private final SkillsShCandidateExport exporter;

    @Autowired
    public SkillsShCandidateExportController(SessionPrincipalResolver principals,
            @Value("${agentfit.skills-sh.snapshot-path:}") String snapshotPath) {
        this(principals, snapshotPath, SkillsShCandidateCollector.publicExperimental()::download);
    }

    SkillsShCandidateExportController(SessionPrincipalResolver principals, String snapshotPath,
            Function<SkillCandidate, SkillsShCandidateCollector.SkillPackage> downloader) {
        this.principals = principals;
        this.snapshotPath = snapshotPath;
        this.exporter = new SkillsShCandidateExport(downloader);
    }

    @PostMapping("/api/tool-candidates/export")
    public ResponseEntity<byte[]> export(HttpServletRequest request, @RequestBody ExportRequest selection) {
        principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        if (snapshotPath == null || snapshotPath.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        Path snapshot = Path.of(snapshotPath);
        if (!Files.isRegularFile(snapshot, LinkOption.NOFOLLOW_LINKS)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (selection == null) throw new IllegalArgumentException("candidate selection required");
        byte[] zip = exporter.export(snapshot, selection.externalId(), selection.contentHash());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=agentfit-skill-candidate.zip")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zip);
    }

    @ExceptionHandler(SkillsShCandidateSnapshot.InvalidSnapshotException.class)
    public ResponseEntity<Map<String, String>> invalidSnapshot() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("code", "SKILL_CANDIDATES_UNAVAILABLE"));
    }

    @ExceptionHandler(SkillsShCandidateCollector.DiscoveryException.class)
    public ResponseEntity<Map<String, String>> remoteFailure(
            SkillsShCandidateCollector.DiscoveryException exception) {
        boolean changed = exception.getMessage() != null
            && exception.getMessage().contains("content changed");
        return ResponseEntity.status(changed ? HttpStatus.CONFLICT : HttpStatus.BAD_GATEWAY)
            .body(Map.of("code", changed ? "SKILL_CANDIDATE_CHANGED" : "SKILL_SOURCE_UNAVAILABLE"));
    }
}
