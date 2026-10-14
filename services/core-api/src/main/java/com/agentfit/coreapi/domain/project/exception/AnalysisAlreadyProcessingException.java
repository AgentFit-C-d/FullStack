package com.agentfit.coreapi.domain.project.exception;
import com.agentfit.coreapi.global.exception.*;
public class AnalysisAlreadyProcessingException extends BusinessException { public AnalysisAlreadyProcessingException() { super(ErrorCode.ANALYSIS_ALREADY_PROCESSING, "분석이 이미 진행 중입니다. 완료될 때까지 기다려 주세요."); } }
