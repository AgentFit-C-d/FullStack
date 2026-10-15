package com.agentfit.coreapi.domain.project;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProjectInputBoundaryTest {
    @Test
    void writeCommandContainsOnlyProjectAndBaseVersion() {
        ProjectWriteCommand command = new ProjectWriteCommand("p-1", 4L);
        assertEquals("p-1", command.projectId());
        assertEquals(4L, command.baseVersion());
    }
}



