package com.agentfit.coreapi.global.exception;


import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF token is invalid"),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "Project not found"),
    INVALID_INPUT(HttpStatus.UNPROCESSABLE_ENTITY, "Request validation failed"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "The project changed; reload and retry"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}


