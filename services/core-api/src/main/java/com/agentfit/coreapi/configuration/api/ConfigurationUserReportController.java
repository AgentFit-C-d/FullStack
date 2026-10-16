package com.agentfit.coreapi.configuration.api;

import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Accepts only an authenticated user's application statement, never verification evidence. */
@RestController
@RequestMapping("/api/projects/{projectId}/configuration-history")
public class ConfigurationUserReportController {
    public record ReportResponse(ConfigurationHistoryReadModel.UserReport report) {}

    private final SessionPrincipalResolver principals;
    private final ProjectOwnershipGuard ownership;
    private final ConfigurationUserReportRuntimeService reports;

    public ConfigurationUserReportController(SessionPrincipalResolver principals,
            ProjectOwnershipGuard ownership, ConfigurationUserReportRuntimeService reports) {
        this.principals = principals;
        this.ownership = ownership;
        this.reports = reports;
    }

    @PostMapping("/{configurationId}/reports")
    public ResponseEntity<ReportResponse> report(HttpServletRequest request,
            @PathVariable String projectId, @PathVariable String configurationId,
            @RequestBody JsonNode body) {
        var principal = principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        ownership.requireOwned(principal, projectId);
        UUID actorId = principal.userUuid();
        if (actorId == null) throw new IllegalArgumentException("authenticated UUID actor required");
        var submitted = parse(body);
        var saved = reports.report(projectId, configurationId, submitted, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(new ReportResponse(saved));
    }

    private static ConfigurationUserReport.Submission parse(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 2
            || !body.has("reportedState") || !body.has("reasonCode")
            || !body.get("reportedState").isTextual()
            || !(body.get("reasonCode").isNull() || body.get("reasonCode").isTextual())) {
            throw new IllegalArgumentException("invalid configuration user report input");
        }
        try {
            var state = ConfigurationUserReport.ReportedState.valueOf(
                body.get("reportedState").textValue());
            var reason = body.get("reasonCode").isNull() ? null
                : ConfigurationUserReport.ReasonCode.valueOf(
                    body.get("reasonCode").textValue());
            return new ConfigurationUserReport.Submission(state, reason);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid configuration user report input", exception);
        }
    }
}
