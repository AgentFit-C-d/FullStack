package com.agentfit.coreapi.global.exception;


import com.agentfit.coreapi.domain.project.exception.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@WebMvcTest(controllers = ApiExceptionHandlerTest.ThrowingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, ApiExceptionHandlerTest.ThrowingController.class})
class ApiExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/test/unauthenticated"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unknownAndForeignProjectsShare404() throws Exception {
        mockMvc.perform(get("/test/project-not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void staleVersionIs409() throws Exception {
        mockMvc.perform(get("/test/version-conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @RestController
    public static class ThrowingController {
        @GetMapping("/test/unauthenticated")
        String unauthenticated() {
            throw new UnauthenticatedException();
        }

        @GetMapping("/test/project-not-found")
        String projectNotFound() {
            throw new ProjectNotFoundException();
        }

        @GetMapping("/test/version-conflict")
        String versionConflict() {
            throw new VersionConflictException(3, 4);
        }
    }
}



