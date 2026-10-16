package com.agentfit.coreapi.catalog;

import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Keeps an unapproved or unavailable release distinct from a valid empty result. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CatalogController.class)
public class CatalogControllerExceptionHandler {
    public record Error(String code, String message, Instant timestamp) {}

    @ExceptionHandler({CatalogBundleLoader.CatalogUnavailableException.class,
        ResponseStatusException.class})
    ResponseEntity<Error> unavailable(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new Error("CATALOG_UNAVAILABLE", "approved Catalog is unavailable", Instant.now()));
    }
}
