package com.agentfit.coreapi.domain.auth;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OAuthLoginSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void oauthStartUsesTheConfiguredGithubPath() throws Exception {
        mockMvc.perform(get("/api/oauth2/authorization/github"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("github.com")));
    }

    @Test
    void oauthFailureDoesNotExposeProviderDetails() throws Exception {
        mockMvc.perform(get("/api/login/oauth2/code/github")
                .param("error", "access_denied")
                .param("error_description", "provider-secret"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/?login=failed"));
    }

    @Test
    void csrfEndpointIssuesReadableTokenCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("XSRF-TOKEN=")));
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isForbidden());
    }
}



