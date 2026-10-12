package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.util.Optional;
import java.util.function.Function;

import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;

/** Ensures a child identifier actually belongs to the URL project boundary. */
public class ChildResourceOwnershipGuard {
    private final Function<String, String> childProjectLookup;

    public ChildResourceOwnershipGuard(Function<String, String> childProjectLookup) {
        this.childProjectLookup = childProjectLookup;
    }

    public void requireBelongs(String projectId, String childId) {
        Optional.ofNullable(childProjectLookup.apply(childId))
            .filter(projectId::equals)
            .orElseThrow(ProjectNotFoundException::new);
    }
}



