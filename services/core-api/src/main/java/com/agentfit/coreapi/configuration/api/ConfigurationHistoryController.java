package com.agentfit.coreapi.configuration.api;

import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryPageService;
import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import com.agentfit.coreapi.persistence.ProjectDbId;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes only metadata, current validity and USER reports for generated configuration ZIPs. */
@RestController
@RequestMapping("/api/projects/{projectId}/configuration-history")
public class ConfigurationHistoryController {
    public record Verification(ConfigurationGenerationHistory.VerificationStatus status,
                               ConfigurationGenerationHistory.VerificationSource source) {}
    public record Item(String id, Instant createdAt, String recommendationId, String previewId,
                       String approvalId, PreviewBasis basis, String fingerprint,
                       ConfigurationGenerationHistory.Validity validity,
                       List<ConfigurationGenerationHistory.FileMetadata> files,
                       ConfigurationGenerationHistory.Status status, Verification verification,
                       ConfigurationHistoryReadModel.UserReport latestUserReport) {}
    public record Page(List<Item> items, String nextCursor) {}

    private final SessionPrincipalResolver principals;
    private final ProjectOwnershipGuard ownership;
    private final ConfigurationHistoryRuntimeService history;

    public ConfigurationHistoryController(SessionPrincipalResolver principals,
            ProjectOwnershipGuard ownership, ConfigurationHistoryRuntimeService history) {
        this.principals = principals;
        this.ownership = ownership;
        this.history = history;
    }

    @GetMapping
    public ResponseEntity<Page> list(HttpServletRequest request, @PathVariable String projectId,
            @RequestParam(defaultValue = "20") String limit,
            @RequestParam(required = false) String cursor) {
        var principal = principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        ownership.requireOwned(principal, projectId);
        if (!limit.matches("[1-9][0-9]{0,2}")) {
            throw new IllegalArgumentException("invalid history limit");
        }
        int size = Integer.parseInt(limit);
        if (size > 100) throw new IllegalArgumentException("invalid history limit");
        var result = history.list(projectId, decode(cursor), size);
        var items = result.items().stream().map(ConfigurationHistoryController::item).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Page(items, encode(result.nextCursor())));
    }

    private static Item item(ConfigurationHistoryReadModel model) {
        var generation = model.generation();
        return new Item(generation.id(), generation.createdAt(), generation.recommendationId(),
            generation.previewId(), generation.approvalId(), generation.basis(),
            generation.fingerprint(), model.validity(), generation.files(), generation.status(),
            new Verification(generation.verificationStatus(), generation.verificationSource()),
            model.latestUserReport());
    }

    static String encode(ConfigurationHistoryPageService.Cursor cursor) {
        if (cursor == null) return null;
        var raw = cursor.generatedAt() + "\n" + cursor.generationId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            raw.getBytes(StandardCharsets.UTF_8));
    }

    static ConfigurationHistoryPageService.Cursor decode(String value) {
        if (value == null) return null;
        if (value.isBlank() || value.length() > 512 || !value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("invalid history cursor");
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\n", -1);
            if (parts.length != 2 || !parts[0].equals(Instant.parse(parts[0]).toString())) {
                throw new IllegalArgumentException("invalid history cursor");
            }
            String id = ProjectDbId.require(parts[1]);
            if (!id.equals(parts[1]) || !encode(new ConfigurationHistoryPageService.Cursor(
                    Instant.parse(parts[0]), id)).equals(value)) {
                throw new IllegalArgumentException("invalid history cursor");
            }
            return new ConfigurationHistoryPageService.Cursor(Instant.parse(parts[0]), id);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid history cursor", exception);
        }
    }
}
