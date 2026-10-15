package com.agentfit.coreapi.domain.project.exception;
import com.agentfit.coreapi.global.exception.*;
public class ActiveAnalysisProjectDeletionException extends BusinessException { public ActiveAnalysisProjectDeletionException() { super(ErrorCode.ANALYSIS_ACTIVE_PROJECT_DELETE_FORBIDDEN); } }
