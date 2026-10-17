package com.agentfit.coreapi.global.exception;

/** B cannot run until A has created a user-confirmed profile snapshot. */
public final class ProfileNotConfirmedException extends BusinessException {
    public ProfileNotConfirmedException() {
        super(ErrorCode.PROFILE_NOT_CONFIRMED);
    }
}
