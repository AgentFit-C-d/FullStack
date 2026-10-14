package com.agentfit.coreapi.domain.project.exception;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import com.agentfit.coreapi.global.exception.BusinessException;
import com.agentfit.coreapi.global.exception.ErrorCode;

public class ProjectNotFoundException extends BusinessException {
    public ProjectNotFoundException() {
        super(ErrorCode.PROJECT_NOT_FOUND);
    }
}



