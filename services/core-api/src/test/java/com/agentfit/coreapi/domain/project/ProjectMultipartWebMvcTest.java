package com.agentfit.coreapi.domain.project;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
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
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.agentfit.coreapi.domain.project.controller.ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.agentfit.coreapi.global.exception.GlobalExceptionHandler.class)
class ProjectMultipartWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean ProjectService projectService;
    @MockBean SessionPrincipalResolver principalResolver;

    @Test void multipartCreateRequiresAndPassesIdempotencyKeyWithoutRawOrOwnerFields() throws Exception {
        var principal = new AuthenticatedPrincipal(7, "Alice", null);
        var project = new Project("p1", 7, "demo", 1, Instant.now(), Instant.now());
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(principal));
        when(projectService.create(any(), anyString(), anyString(), anyString(), any(), any(), eq("k-1"))).thenReturn(project);

        var metadata = new MockPart("metadata", "{\"name\":\"demo\",\"role\":\"BACKEND\",\"runtimes\":\"Java\\nSpring\"}".getBytes());
        metadata.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(multipart("/api/projects")
                .file(new MockMultipartFile("file", "plan.md", "text/markdown", "# plan".getBytes()))
                .part(metadata)
                .header("Idempotency-Key", "k-1"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.project.id").value("p1"))
            .andExpect(jsonPath("$.project.ownerId").doesNotExist())
            .andExpect(jsonPath("$.project.rawContent").doesNotExist());
        verify(projectService).create(any(), eq("demo"), eq("BACKEND"), eq("Java\nSpring"), any(), isNull(), eq("k-1"));
    }

    @Test void unauthenticatedMultipartCreateIsRejected() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.empty());
        var metadata = new MockPart("metadata", "{\"name\":\"demo\",\"role\":\"BACKEND\"}".getBytes());
        metadata.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(multipart("/api/projects").part(metadata).header("Idempotency-Key", "k-2"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(projectService);
    }
}
