package com.agentfit.coreapi.domain.auth;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SessionPrincipalResolverTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedSessionIsReturnedAsJsonNull() throws Exception {
        mockMvc.perform(get("/api/auth/get-session"))
            .andExpect(status().isOk())
            .andExpect(content().string("null"));
    }
}



