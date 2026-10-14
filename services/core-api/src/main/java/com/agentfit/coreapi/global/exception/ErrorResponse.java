package com.agentfit.coreapi.global.exception;


import java.time.Instant;

public record ErrorResponse(String code, String message, Instant timestamp) {
    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(),
            message == null || message.isBlank() ? code.defaultMessage() : message,
            Instant.now());
    }

    /** Contract-compatible nested error view while retaining the flat MVP fields. */
    public ErrorBody error() {
        return new ErrorBody(code, message, "unknown");
    }

    public record ErrorBody(String code, String message, String requestId) {
    }
}


