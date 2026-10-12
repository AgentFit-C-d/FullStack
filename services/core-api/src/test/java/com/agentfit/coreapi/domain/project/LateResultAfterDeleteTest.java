package com.agentfit.coreapi.domain.project;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LateResultAfterDeleteTest {
    @Test
    void deletedProjectCannotAcceptLateResult() {
        ResultPersistenceGuard guard = new ResultPersistenceGuard(projectExists -> projectExists);
        assertFalse(guard.canPersist(false));
    }
}



