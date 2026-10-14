package com.agentfit.coreapi.domain.auth.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.time.Instant;
import java.util.Optional;

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

        long userId;
        try {
            userId = Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        Optional<UserAccount> account = userAccountRepository.findById(userId);
        if (account.isEmpty()) {
            return Optional.empty();
        }

        HttpSession session = request.getSession(false);
        Instant expiresAt = session == null ? null
            : Instant.ofEpochMilli(session.getLastAccessedTime())
                .plusSeconds(Math.max(0, session.getMaxInactiveInterval()));
        return Optional.of(new AuthenticatedPrincipal(account.get().id(), account.get().name(), expiresAt));
    }
}



