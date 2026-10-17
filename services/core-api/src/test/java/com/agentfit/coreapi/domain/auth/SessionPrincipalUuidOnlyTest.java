package com.agentfit.coreapi.domain.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.agentfit.coreapi.domain.auth.entity.UserAccount;
import com.agentfit.coreapi.domain.auth.repository.UserAccountRepository;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionPrincipalUuidOnlyTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsLegacyNumericSessionPrincipal() {
        UserAccountRepository accounts = new UserAccountRepository() {
            @Override public Optional<UserAccount> findByGithubUserId(long githubUserId) { return Optional.empty(); }
            @Override public UserAccount createFromGithub(long githubUserId, String githubLogin, String displayName) {
                throw new UnsupportedOperationException();
            }
        };
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("7", "n/a", java.util.List.of()));

        assertTrue(new SessionPrincipalResolver(accounts).resolve(mock(HttpServletRequest.class)).isEmpty());
    }
}
