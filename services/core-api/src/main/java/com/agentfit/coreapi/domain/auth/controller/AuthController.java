package com.agentfit.coreapi.domain.auth.controller;


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
public class AuthController {
    private final SessionPrincipalResolver sessionPrincipalResolver;

    public AuthController(SessionPrincipalResolver sessionPrincipalResolver) {
        this.sessionPrincipalResolver = sessionPrincipalResolver;
    }

    @GetMapping("/api/auth/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        if (token != null) {
            token.getToken();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/auth/get-session")
    public ResponseEntity<?> getSession(HttpServletRequest request) {
        var principal = sessionPrincipalResolver.resolve(request);
        if (principal.isEmpty()) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null");
        }
        var user = principal.get();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new PublicSession(
            new PublicUser(user.userId(), user.name()),
            new PublicSessionMetadata(user.sessionExpiresAt())));
    }

    public record PublicUser(long id, String name) {
    }

    public record PublicSessionMetadata(Instant expiresAt) {
    }

    public record PublicSession(PublicUser user, PublicSessionMetadata session) {
    }
}



