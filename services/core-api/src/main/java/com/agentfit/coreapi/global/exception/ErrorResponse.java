package com.agentfit.coreapi.global.exception;


import java.time.Instant;

public record ErrorResponse(String code, String message, Instant timestamp) {
    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(),
            message == null || message.isBlank() ? code.defaultMessage() : message,
            Instant.now());
    }
}


