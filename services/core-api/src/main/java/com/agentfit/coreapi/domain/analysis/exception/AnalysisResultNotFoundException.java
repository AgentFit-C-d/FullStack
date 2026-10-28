package com.agentfit.coreapi.domain.analysis.exception;

import com.agentfit.coreapi.global.exception.BusinessException;
import com.agentfit.coreapi.global.exception.ErrorCode;

public class AnalysisResultNotFoundException extends BusinessException {
    public AnalysisResultNotFoundException() {
        super(ErrorCode.ANALYSIS_RESULT_NOT_FOUND);
    }
}
