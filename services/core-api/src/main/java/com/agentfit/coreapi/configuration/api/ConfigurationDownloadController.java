package com.agentfit.coreapi.configuration.api;

import com.agentfit.coreapi.catalog.CatalogPreviewRequest;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B-owned ZIP API after A session authentication and project ownership check. */
@RestController
@RequestMapping("/api/projects/{projectId}/config-approvals")
public class ConfigurationDownloadController {
    public record DownloadRequest(String previewId, String fingerprint,
                                  CatalogPreviewRequest previewRequest) {}

    private final SessionPrincipalResolver principals;
    private final ProjectOwnershipGuard ownership;
    private final ConfigurationDownloadRuntimeService downloads;

    public ConfigurationDownloadController(SessionPrincipalResolver principals,
            ProjectOwnershipGuard ownership, ConfigurationDownloadRuntimeService downloads) {
        this.principals = principals;
        this.ownership = ownership;
        this.downloads = downloads;
    }

    @PostMapping("/{approvalId}/download")
    public ResponseEntity<byte[]> download(HttpServletRequest request, @PathVariable String projectId,
            @PathVariable String approvalId, @RequestBody DownloadRequest body) {
        var principal = principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        ownership.requireOwned(principal, projectId);
        var generated = downloads.download(projectId, approvalId, body.previewId(),
            body.fingerprint(), body.previewRequest(), principal.userUuid());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"agentfit-config.zip\"")
            .header("X-Config-Export-Id", generated.exportId())
            .header("X-Unmet-Capability-Keys", String.join(",", generated.unmetCapabilityKeys()))
            .body(generated.zipBytes());
    }
}
