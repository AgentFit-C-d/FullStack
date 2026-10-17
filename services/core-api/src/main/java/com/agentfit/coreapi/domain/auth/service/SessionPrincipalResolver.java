package com.agentfit.coreapi.domain.auth.service;


import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SessionPrincipalResolver {
    private final UserAccountRepository userAccountRepository;

    public SessionPrincipalResolver(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public Optional<AuthenticatedPrincipal> resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        String principalName = authentication.getName();
        Optional<UserAccount> accountByUuid;
        try {
            accountByUuid = userAccountRepository.findByUuid(UUID.fromString(principalName));
        } catch (IllegalArgumentException ex) {
            accountByUuid = Optional.empty();
        }
        if (accountByUuid.isPresent()) {
            HttpSession session = request.getSession(false);
            Instant expiresAt = session == null ? null
                : Instant.ofEpochMilli(session.getLastAccessedTime())
                    .plusSeconds(Math.max(0, session.getMaxInactiveInterval()));
            return Optional.of(new AuthenticatedPrincipal(accountByUuid.get(), expiresAt));
        }

        return Optional.empty();
    }
}



