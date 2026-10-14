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
import java.time.Instant;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;

public class ResultPersistenceGuard {
    private final Predicate<Boolean> projectStateCheck;

    public ResultPersistenceGuard(Predicate<Boolean> projectStateCheck) {
        this.projectStateCheck = projectStateCheck;
    }

    public boolean canPersist(boolean projectExistsAndValid) {
        return projectStateCheck.test(projectExistsAndValid);
    }

    public boolean canPersist(AnalysisAttemptContext context, Project project, Instant now) {
        if (context == null || project == null || now == null) {
            return false;
        }
        boolean matches = context.projectId().equals(project.id())
            && context.generation() == project.analysisGeneration()
            && !"DELETED".equals(project.stage())
            && context.isAcceptable(now);
        return projectStateCheck.test(matches);
    }
}



