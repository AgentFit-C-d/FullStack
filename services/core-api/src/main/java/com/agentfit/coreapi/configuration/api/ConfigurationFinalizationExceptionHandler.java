package com.agentfit.coreapi.configuration.api;

import com.agentfit.coreapi.configuration.export.PreviewFreshnessGate;
import com.agentfit.coreapi.global.exception.ErrorCode;
import com.agentfit.coreapi.global.exception.ErrorResponse;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Gives the frontend a reload-and-retry signal for stale or expired finalization. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
    ConfigurationApprovalController.class, ConfigurationDownloadController.class,
    ConfigurationUserReportController.class, ConfigurationHistoryController.class
})
public class ConfigurationFinalizationExceptionHandler {
    public record FinalizationError(String code, String message, Instant timestamp) {}

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<FinalizationError> unavailable(ResponseStatusException exception) {
        String code = exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
            ? "CONFIGURATION_NOT_FOUND" : "CATALOG_UNAVAILABLE";
        return ResponseEntity.status(exception.getStatusCode())
            .body(new FinalizationError(code, exception.getReason(), Instant.now()));
    }

    @ExceptionHandler({PreviewFreshnessGate.StalePreviewException.class,
        PreviewFreshnessGate.ExpiredPreviewException.class})
    ResponseEntity<ErrorResponse> stale(RuntimeException exception) {
        return ResponseEntity.status(ErrorCode.VERSION_CONFLICT.status())
            .body(ErrorResponse.of(ErrorCode.VERSION_CONFLICT, exception.getMessage()));
    }
}
