package com.agentfit.coreapi.domain.auth.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class GitHubOAuth2UserService extends DefaultOAuth2UserService {
    private final UserAccountRepository userAccountRepository;

    public GitHubOAuth2UserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User providerUser = super.loadUser(userRequest);
        long githubUserId = parseGithubId(providerUser.getAttribute("id"));
        String githubLogin = providerUser.getAttribute("login");
        String displayName = providerUser.getAttribute("name");
        if (displayName == null || displayName.isBlank()) {
            displayName = githubLogin == null || githubLogin.isBlank() ? "GitHub user" : githubLogin;
        }
        final String resolvedDisplayName = displayName;

        UserAccount account = userAccountRepository.findByGithubUserId(githubUserId)
            .orElseGet(() -> userAccountRepository.createFromGithub(githubUserId, githubLogin, resolvedDisplayName));

        // The provider access token is intentionally not copied into attributes or persistence.
        Map<String, Object> safeAttributes = Map.of("id", githubUserId, "login", githubLogin == null ? "" : githubLogin,
            "name", account.name());
        return new AgentFitOAuth2User(account, safeAttributes,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private long parseGithubId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ex) {
            throw new OAuth2AuthenticationException(new org.springframework.security.oauth2.core.OAuth2Error(
                "invalid_user_info"), "GitHub user id is missing or invalid");
        }
    }
}



