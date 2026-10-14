package com.agentfit.coreapi.domain.project;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;

class CrossProjectChildIsolationTest {
    @Test
    void childFromAnotherProjectIsRejected() {
        ChildResourceOwnershipGuard guard = new ChildResourceOwnershipGuard(
            childId -> Map.of("draft-a", "project-a", "draft-b", "project-b").get(childId));

        assertThrows(ProjectNotFoundException.class,
            () -> guard.requireBelongs("project-a", "draft-b"));
        guard.requireBelongs("project-a", "draft-a");
    }
}



