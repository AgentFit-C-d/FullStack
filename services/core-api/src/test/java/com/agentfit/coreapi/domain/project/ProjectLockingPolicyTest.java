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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.agentfit.coreapi.global.exception.VersionConflictException;

class ProjectLockingPolicyTest {
    @Test
    void locksProjectBeforeCheckingVersionAndDoesNotWriteWhenStale() {
        List<String> calls = new ArrayList<>();
        ProjectLockingPolicy.LockedProjectRepository repository = projectId -> {
            calls.add("lock:" + projectId);
            return new ProjectLockingPolicy.LockedProject(projectId, 4);
        };
        ProjectLockingPolicy policy = new ProjectLockingPolicy(repository);

        assertThrows(VersionConflictException.class,
            () -> policy.execute(12, 3, () -> calls.add("write")));

        assertEquals(List.of("lock:12"), calls);
    }
}



