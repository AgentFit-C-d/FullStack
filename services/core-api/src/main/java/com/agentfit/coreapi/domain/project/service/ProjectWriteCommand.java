package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
public record ProjectWriteCommand(String projectId, long baseVersion) {
    public ProjectWriteCommand {
        if (projectId == null || projectId.isBlank() || baseVersion < 1) {
            throw new IllegalArgumentException("projectId and positive baseVersion are required");
        }
    }
}



