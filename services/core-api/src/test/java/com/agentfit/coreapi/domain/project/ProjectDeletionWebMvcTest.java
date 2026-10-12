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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

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
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import com.agentfit.coreapi.global.exception.VersionConflictException;
import jakarta.servlet.http.HttpServletRequest;

@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProjectDeletionWebMvcTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ProjectService projectService;
    @MockBean private ProjectDeletionService deletionService;
    @MockBean private SessionPrincipalResolver principalResolver;

    @Test
    void staleDeleteReturns409() throws Exception {
        when(principalResolver.resolve(any(HttpServletRequest.class)))
            .thenReturn(Optional.of(new AuthenticatedPrincipal(7L, "owner", null)));
        doThrow(new VersionConflictException(1L, 2L)).when(deletionService)
            .delete(any(AuthenticatedPrincipal.class), eq("p-1"), eq(true), eq(1L));

        mockMvc.perform(delete("/api/projects/p-1")
                .contentType("application/json")
                .content("{\"confirmation\":true,\"expectedVersion\":1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void unconfirmedDeleteIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(delete("/api/projects/p-1")
                .contentType("application/json")
                .content("{\"confirmation\":false,\"expectedVersion\":1}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void missingExpectedVersionIsRejectedBeforeDeletion() throws Exception {
        mockMvc.perform(delete("/api/projects/p-1")
                .contentType("application/json")
                .content("{\"confirmation\":true}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}



