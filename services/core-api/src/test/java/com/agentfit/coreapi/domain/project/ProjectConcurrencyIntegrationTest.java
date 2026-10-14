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
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

class ProjectConcurrencyIntegrationTest {
    @Test
    void staleConcurrentBaseVersionIsRejectedBySharedPolicy() {
        ProjectLockingPolicy policy = new ProjectLockingPolicy(id -> new ProjectLockingPolicy.LockedProject(id, 2L));
        assertThrows(com.agentfit.coreapi.global.exception.VersionConflictException.class,
            () -> policy.execute(1L, 1L, () -> "not-written"));
    }

    @Test
    void projectLockAndVersionGateAllowOnlyOneMutationForTheSameBaseVersion() {
        AtomicInteger version = new AtomicInteger(1);
        AtomicInteger writes = new AtomicInteger();
        ProjectLockingPolicy policy = new ProjectLockingPolicy(id ->
            new ProjectLockingPolicy.LockedProject(id, version.get()));

        synchronized (version) {
            policy.execute(1L, 1L, () -> {
                writes.incrementAndGet();
                version.incrementAndGet();
                return null;
            });
            assertThrows(com.agentfit.coreapi.global.exception.VersionConflictException.class,
                () -> policy.execute(1L, 1L, () -> {
                    writes.incrementAndGet();
                    return null;
                }));
        }
        assertEquals(1, writes.get());
        assertEquals(2, version.get());
    }
}



