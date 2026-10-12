package com.agentfit.coreapi.contract;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.project.dto.response.ProjectResponse;
import com.agentfit.coreapi.domain.project.dto.response.ProjectDetailResponse;
import com.agentfit.coreapi.domain.project.dto.request.ProjectDeleteRequest;
import com.agentfit.coreapi.global.exception.ErrorCode;
import com.agentfit.coreapi.global.exception.ErrorResponse;

class ProjectOwnershipContractTest {
    @Test
    void publicProjectResponseDoesNotExposeOwnerId() {
        ProjectResponse response = ProjectResponse.from("p-1", "demo", 1L,
            Instant.parse("2026-10-08T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"));
        assertFalse(response.toString().contains("ownerId"));
    }

    @Test
    void detailAndDeleteWireShapesMatchTheContract() {
        ProjectResponse response = ProjectResponse.from("p-1", "demo", 1L,
            Instant.parse("2026-10-08T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"));
        ProjectDetailResponse detail = new ProjectDetailResponse(response, null, null, null);
        assertEquals(null, detail.confirmed());
        assertEquals(true, new ProjectDeleteRequest(true, 1L).confirmation());
        assertEquals("PROJECT_NOT_FOUND", ErrorResponse.of(ErrorCode.PROJECT_NOT_FOUND, null).error().code());
    }
}



