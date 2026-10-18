package com.agentfit.coreapi.recommendation.controller;

import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Preserve the requested HTTP status at B's recommendation boundary. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = RecommendationController.class)
public class RecommendationResponseStatusHandler {
    public record Error(String code, String message, Instant timestamp) {}

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Error> responseStatus(ResponseStatusException exception) {
        String code = exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
            ? "RECOMMENDATION_NOT_FOUND" : "RECOMMENDATION_UNAVAILABLE";
        return ResponseEntity.status(exception.getStatusCode())
            .body(new Error(code, exception.getReason(), Instant.now()));
    }
}
