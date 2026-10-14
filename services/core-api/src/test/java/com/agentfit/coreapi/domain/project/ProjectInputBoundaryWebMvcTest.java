package com.agentfit.coreapi.domain.project;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.agentfit.coreapi.domain.project.controller.ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.agentfit.coreapi.global.exception.GlobalExceptionHandler.class)
class ProjectInputBoundaryWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean ProjectService projectService;
    @MockBean SessionPrincipalResolver principalResolver;

    @Test void serviceValidationFailureReturns422WithoutResponseData() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(new AuthenticatedPrincipal(7, "u", null)));
        when(projectService.create(any(), nullable(String.class), nullable(String.class), nullable(String.class), any(), nullable(String.class), nullable(String.class))).thenThrow(new IllegalArgumentException("Exactly one planning input is required"));
        var metadata = new MockPart("metadata", "{\"name\":\"demo\",\"role\":\"BACKEND\"}".getBytes());
        metadata.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(multipart("/api/projects").part(metadata).header("Idempotency-Key", "k"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
