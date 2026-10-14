package com.agentfit.coreapi.domain.auth;

import com.agentfit.coreapi.domain.auth.entity.AgentFitOAuth2User;
import com.agentfit.coreapi.domain.auth.entity.UserAccount;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AgentFitOAuth2UserSerializationTest {

    @Test
    void authenticationPrincipalCanBeStoredInJdbcSession() {
        AgentFitOAuth2User user = new AgentFitOAuth2User(
            new UserAccount(1L, "GitHub user", 12345L, "github-user"),
            Map.of("id", 12345L, "login", "github-user", "name", "GitHub user"),
            List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertDoesNotThrow(() -> {
            try (var output = new ObjectOutputStream(new ByteArrayOutputStream())) {
                output.writeObject(user);
            }
        });
    }
}
