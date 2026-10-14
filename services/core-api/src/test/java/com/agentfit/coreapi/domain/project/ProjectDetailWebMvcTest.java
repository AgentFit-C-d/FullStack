package com.agentfit.coreapi.domain.project;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.dto.response.*;
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
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(com.agentfit.coreapi.domain.project.controller.ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.agentfit.coreapi.global.exception.GlobalExceptionHandler.class)
class ProjectDetailWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean ProjectService projectService;
    @MockBean SessionPrincipalResolver principalResolver;

    @Test void detailUpdateAndInputReplacementExposeOnlySafeMetadata() throws Exception {
        var principal = new AuthenticatedPrincipal(7, "u", null);
        when(principalResolver.resolve(any(HttpServletRequest.class))).thenReturn(Optional.of(principal));
        var project = new Project("p1", 7, "demo", 2, Instant.now(), Instant.now());
        var detail = new ProjectDetailResponse(new ProjectResponse("p1", "demo", 2, project.createdAt(), project.updatedAt(), "BACKEND", java.util.List.of("Java")), new PlanningInputMetadataResponse(1, "TEXT", null, "text/plain", 4L, 4, null), null, null);
        when(projectService.detail(principal, "p1")).thenReturn(detail);

        mockMvc.perform(get("/api/projects/p1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.project.role").value("BACKEND"))
            .andExpect(jsonPath("$.project.runtimes[0]").value("Java"))
            .andExpect(jsonPath("$.confirmed.inputType").value("TEXT"))
            .andExpect(jsonPath("$.confirmed.rawContent").doesNotExist());

        mockMvc.perform(patch("/api/projects/p1").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2,\"name\":\"changed\"}"))
            .andExpect(status().isOk());


    }

}
