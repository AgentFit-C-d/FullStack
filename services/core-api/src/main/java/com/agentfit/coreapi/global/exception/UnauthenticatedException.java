package com.agentfit.coreapi.global.exception;


import com.agentfit.coreapi.domain.project.exception.*;
public class UnauthenticatedException extends BusinessException {
    public UnauthenticatedException() {
        super(ErrorCode.UNAUTHENTICATED);
    }
}



