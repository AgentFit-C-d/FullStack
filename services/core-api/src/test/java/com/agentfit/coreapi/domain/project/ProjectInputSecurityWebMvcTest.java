package com.agentfit.coreapi.domain.project;

import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.agentfit.coreapi.domain.project.controller.ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.agentfit.coreapi.global.exception.GlobalExceptionHandler.class)
class ProjectInputSecurityWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean ProjectService projectService;
    @MockBean SessionPrincipalResolver principalResolver;

    @Test void missingSessionRejectsProjectMutations() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.empty());
        mockMvc.perform(patch("/api/projects/p1").contentType("application/json").content("{\"expectedVersion\":1}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(projectService);
    }

    @Test void foreignProjectIsHiddenAsNotFound() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(new com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal(7, "u", null)));
        when(projectService.detail(any(), eq("foreign"))).thenThrow(new com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException());
        mockMvc.perform(get("/api/projects/foreign")).andExpect(status().isNotFound());
    }
}
