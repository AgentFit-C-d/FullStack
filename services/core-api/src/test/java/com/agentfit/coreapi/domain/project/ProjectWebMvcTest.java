package com.agentfit.coreapi.domain.project;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.controller.ProjectController;
import jakarta.servlet.http.HttpServletRequest;

@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.agentfit.coreapi.global.exception.GlobalExceptionHandler.class)
class ProjectWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private ProjectDeletionService deletionService;

    @MockBean
    private SessionPrincipalResolver principalResolver;

    @Test
    void createResponseDoesNotExposeOwnerId() throws Exception {
        Project project = new Project("p-1", 7L, "demo", 1L,
            Instant.parse("2026-10-08T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"));
        when(principalResolver.resolve(any(HttpServletRequest.class)))
            .thenReturn(Optional.of(new AuthenticatedPrincipal(7L, "Alice", null)));
        when(projectService.create(any(AuthenticatedPrincipal.class), eq("demo"))).thenReturn(project);

        mockMvc.perform(post("/api/projects")
                .contentType("application/json")
                .content("{\"name\":\"demo\",\"ownerId\":999}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.project.id").value("p-1"))
            .andExpect(jsonPath("$.project.ownerId").doesNotExist());
    }

    @Test
    void listAndDetailUseAuthenticatedOwnerBoundary() throws Exception {
        Project project = new Project("p-1", 7L, "demo", 1L,
            Instant.parse("2026-10-08T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"));
        when(principalResolver.resolve(any(HttpServletRequest.class)))
            .thenReturn(Optional.of(new AuthenticatedPrincipal(7L, "Alice", null)));
        when(projectService.list(any(AuthenticatedPrincipal.class))).thenReturn(List.of(project));
        when(projectService.get(any(AuthenticatedPrincipal.class), eq("p-1"))).thenReturn(project);

        mockMvc.perform(get("/api/projects"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projects[0].id").value("p-1"));
        mockMvc.perform(get("/api/projects/p-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.project.name").value("demo"));
    }

    @Test
    void blankNameReturnsValidationError() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class)))
            .thenReturn(Optional.of(new AuthenticatedPrincipal(7L, "Alice", null)));
        when(projectService.create(any(AuthenticatedPrincipal.class), eq("")))
            .thenThrow(new IllegalArgumentException("blank"));

        mockMvc.perform(post("/api/projects")
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}



