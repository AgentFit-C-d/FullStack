package com.agentfit.coreapi.global.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesPublicOpenApiDocumentWithProjectEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").value("3.0.3"))
            .andExpect(jsonPath("$.info.title").value("AgentFit Core API"))
            .andExpect(jsonPath("$.paths['/api/projects']").exists())
            .andExpect(jsonPath("$.paths['/api/projects/{projectId}/analysis-attempts']").exists());
    }

    @Test
    void groupsProjectEndpointsUnderProjectsTag() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/projects'].get.tags[0]").value("Projects"));
    }

    @Test
    void documentsPublicEndpointsWithoutAuthenticationAndProtectedWritesWithCsrf() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/auth/csrf'].get.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/health'].get.security").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/projects'].get.security[0].agentfitSession").exists())
            .andExpect(jsonPath("$.paths['/api/projects'].post.security[0].agentfitSession").exists())
            .andExpect(jsonPath("$.paths['/api/projects'].post.security[0].agentfitCsrf").exists());
    }

    @Test
    void exposesSwaggerUiPage() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
