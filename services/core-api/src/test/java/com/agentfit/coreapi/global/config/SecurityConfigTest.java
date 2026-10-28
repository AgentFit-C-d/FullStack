package com.agentfit.coreapi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    @Test
    void sends_a_successful_oauth_login_back_to_the_frontend_origin() {
        assertThat(SecurityConfig.frontendRedirect("http://localhost:5173", "success"))
            .isEqualTo("http://localhost:5173/?login=success");
    }

    @Test
    void avoids_a_double_slash_when_the_frontend_origin_has_a_trailing_slash() {
        assertThat(SecurityConfig.frontendRedirect("https://agentfit.example/", "failed"))
            .isEqualTo("https://agentfit.example/?login=failed");
    }
}
