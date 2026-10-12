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

import org.junit.jupiter.api.Test;

class ProjectConcurrencyIntegrationTest {
    @Test
    void staleConcurrentBaseVersionIsRejectedBySharedPolicy() {
        ProjectLockingPolicy policy = new ProjectLockingPolicy(id -> new ProjectLockingPolicy.LockedProject(id, 2L));
        assertThrows(com.agentfit.coreapi.global.exception.VersionConflictException.class,
            () -> policy.execute(1L, 1L, () -> "not-written"));
    }
}



