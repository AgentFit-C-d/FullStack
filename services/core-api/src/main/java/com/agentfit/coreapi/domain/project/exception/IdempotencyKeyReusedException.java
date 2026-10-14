package com.agentfit.coreapi.domain.project.exception;
import com.agentfit.coreapi.global.exception.*;
public class IdempotencyKeyReusedException extends BusinessException { public IdempotencyKeyReusedException() { super(ErrorCode.IDEMPOTENCY_KEY_REUSED); } }
