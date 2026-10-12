package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.util.Set;

public final class ProjectDeletionScope {
    private ProjectDeletionScope() {
    }

    public static final Set<String> DELETED = Set.of(
        "PROFILE", "DOCUMENT_METADATA", "ANALYSIS_ATTEMPT", "REVIEW", "RECOMMENDATION", "PROJECT_SETTINGS", "EXPORT", "AUDIT");
    public static final Set<String> PRESERVED = Set.of("CATALOG", "USER", "SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES", "USER_FILES");
}



