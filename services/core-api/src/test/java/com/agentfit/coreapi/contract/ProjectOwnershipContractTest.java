package com.agentfit.coreapi.contract;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.project.dto.response.ProjectResponse;

class ProjectOwnershipContractTest {
    @Test
    void publicProjectResponseDoesNotExposeOwnerId() {
        ProjectResponse response = ProjectResponse.from("p-1", "demo", 1L,
            Instant.parse("2026-10-08T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"));
        assertFalse(response.toString().contains("ownerId"));
    }
}



