package com.agentfit.coreapi.global.exception;


import com.agentfit.coreapi.domain.project.exception.*;
public class VersionConflictException extends BusinessException {
    private final long expectedVersion;
    private final long actualVersion;

    public VersionConflictException(long expectedVersion, long actualVersion) {
        super(ErrorCode.VERSION_CONFLICT, "The project changed; reload and retry");
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long actualVersion() {
        return actualVersion;
    }
}



