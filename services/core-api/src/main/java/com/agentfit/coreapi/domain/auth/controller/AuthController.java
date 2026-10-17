package com.agentfit.coreapi.domain.auth.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Authentication", description = "Browser session and CSRF token endpoints")
public class AuthController {
    private final SessionPrincipalResolver sessionPrincipalResolver;

    public AuthController(SessionPrincipalResolver sessionPrincipalResolver) {
        this.sessionPrincipalResolver = sessionPrincipalResolver;
    }

    @GetMapping("/api/auth/csrf")
    @Operation(summary = "Issue a CSRF token", description = "Public endpoint. Call this before a state-changing request to receive the XSRF-TOKEN cookie. Swagger UI mirrors that cookie into the X-XSRF-TOKEN header automatically.")
    @ApiResponse(responseCode = "204", description = "CSRF cookie issued")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        if (token != null) {
            token.getToken();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/auth/get-session")
    @Operation(summary = "Get the current browser session", description = "Public endpoint. Returns the public session object for an authenticated browser, or the JSON literal null when no session exists.")
    @ApiResponse(responseCode = "200", description = "Current session or null when unauthenticated",
        content = @Content(schema = @Schema(implementation = PublicSession.class)))
    public ResponseEntity<?> getSession(HttpServletRequest request) {
        var principal = sessionPrincipalResolver.resolve(request);
        if (principal.isEmpty()) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null");
        }
        var user = principal.get();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new PublicSession(
            new PublicUser(user.userUuid().toString(), user.name()),
            new PublicSessionMetadata(user.sessionExpiresAt())));
    }

    public record PublicUser(String id, String name) {
    }

    public record PublicSessionMetadata(Instant expiresAt) {
    }

    public record PublicSession(PublicUser user, PublicSessionMetadata session) {
    }
}



