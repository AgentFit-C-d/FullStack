package com.agentfit.coreapi.configuration.api;

import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP boundary for B's persisted Preview approval. */
@RestController
@RequestMapping("/api/projects/{projectId}/config-previews")
public class ConfigurationApprovalController {
    public record ApprovalRequest(String fingerprint, boolean confirmed) {}

    private final SessionPrincipalResolver principals;
    private final ProjectOwnershipGuard ownership;
    private final ConfigurationApprovalService approvals;

    public ConfigurationApprovalController(SessionPrincipalResolver principals,
            ProjectOwnershipGuard ownership, ConfigurationApprovalService approvals) {
        this.principals = principals;
        this.ownership = ownership;
        this.approvals = approvals;
    }

    @PostMapping("/{previewId}/approval")
    public ResponseEntity<ConfigurationApprovalService.Result> approve(HttpServletRequest request,
            @PathVariable String projectId, @PathVariable String previewId,
            @RequestBody ApprovalRequest body) {
        var principal = principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        ownership.requireOwned(principal, projectId);
        UUID actorId = principal.userUuid();
        if (actorId == null) throw new IllegalArgumentException("authenticated UUID actor required");
        var result = approvals.approve(projectId, previewId, body.fingerprint(),
            body.confirmed(), actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
