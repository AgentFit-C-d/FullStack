package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User-owned role/environment declarations used by the B recommendation snapshot. */
@RestController
@RequestMapping("/api/projects/{projectId}")
public final class RecommendationInputsController {
    public record DeveloperRequest(long expectedVersion, String role, List<String> responsibilities) {}
    public record EnvironmentRequest(long expectedVersion, String osFamily, String clientId,
                                     String clientVersion, boolean installedComponentsKnown,
                                     List<RecommendationInputsService.InstalledComponent> installedComponents,
                                     String osVersion, String configurationKnowledge, Boolean runtimesKnown,
                                     List<RecommendationInputsService.Runtime> runtimes,
                                     List<RecommendationInputsService.AuthReadiness> authReadiness) {}
    public record SavedVersion(long version) {}

    private final SessionPrincipalResolver principals;
    private final ProjectOwnershipGuard ownership;
    private final RecommendationInputsService inputs;

    public RecommendationInputsController(SessionPrincipalResolver principals,
            ProjectOwnershipGuard ownership, RecommendationInputsService inputs) {
        this.principals = principals;
        this.ownership = ownership;
        this.inputs = inputs;
    }

    @GetMapping("/recommendation-inputs")
    public RecommendationInputsService.Current current(HttpServletRequest request,
            @PathVariable String projectId) {
        ownership.requireOwned(principals.resolve(request).orElseThrow(UnauthenticatedException::new), projectId);
        return inputs.current(projectId);
    }

    @PutMapping("/developer-profile")
    public SavedVersion developer(HttpServletRequest request, @PathVariable String projectId,
            @RequestBody DeveloperRequest body) {
        ownership.requireOwned(principals.resolve(request).orElseThrow(UnauthenticatedException::new), projectId);
        return new SavedVersion(inputs.saveDeveloper(projectId, body.expectedVersion(), body.role(),
            body.responsibilities()));
    }

    @PutMapping("/environment-profile")
    public SavedVersion environment(HttpServletRequest request, @PathVariable String projectId,
            @RequestBody EnvironmentRequest body) {
        ownership.requireOwned(principals.resolve(request).orElseThrow(UnauthenticatedException::new), projectId);
        if (body.osVersion() == null && body.configurationKnowledge() == null && body.runtimesKnown() == null
            && body.runtimes() == null && body.authReadiness() == null) {
            return new SavedVersion(inputs.saveEnvironment(projectId, body.expectedVersion(),
                body.osFamily(), body.clientId(), body.clientVersion(), body.installedComponentsKnown(),
                body.installedComponents()));
        }
        return new SavedVersion(inputs.saveEnvironment(projectId, body.expectedVersion(),
            body.osFamily(), body.clientId(), body.clientVersion(), body.installedComponentsKnown(),
            body.installedComponents(), body.osVersion(),
            body.configurationKnowledge() == null ? "UNKNOWN" : body.configurationKnowledge(),
            Boolean.TRUE.equals(body.runtimesKnown()), body.runtimes() == null ? List.of() : body.runtimes(),
            body.authReadiness() == null ? List.of() : body.authReadiness()));
    }
}
