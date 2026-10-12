package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.util.function.Predicate;

public class ResultPersistenceGuard {
    private final Predicate<Boolean> projectStateCheck;

    public ResultPersistenceGuard(Predicate<Boolean> projectStateCheck) {
        this.projectStateCheck = projectStateCheck;
    }

    public boolean canPersist(boolean projectExistsAndValid) {
        return projectStateCheck.test(projectExistsAndValid);
    }
}



