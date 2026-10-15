package com.agentfit.coreapi.domain.analysis.service;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import java.time.Instant;
import java.util.Optional;

/** Validated in-memory key ring; secrets are never exposed in logs or domain records. */
public final class HmacKeyRing {
    private final Key current;
    private final Key previous;

    private HmacKeyRing(Configuration configuration) {
        this.current = new Key(configuration.currentKeyId(), configuration.currentSecret(), null);
        this.previous = configuration.previousKeyId() == null ? null
            : new Key(configuration.previousKeyId(), configuration.previousSecret(), configuration.previousValidUntil());
    }

    public static HmacKeyRing from(Configuration configuration) {
        if (configuration == null || blank(configuration.currentKeyId()) || blank(configuration.currentSecret())) {
            throw new IllegalArgumentException("Current HMAC key id and secret must be provided together");
        }
        boolean anyPrevious = configuration.previousKeyId() != null || configuration.previousSecret() != null
            || configuration.previousValidUntil() != null;
        boolean completePrevious = !blank(configuration.previousKeyId()) && !blank(configuration.previousSecret())
            && configuration.previousValidUntil() != null;
        if (anyPrevious && !completePrevious) {
            throw new IllegalArgumentException("Previous HMAC key id, secret, and validUntil are required together");
        }
        if (completePrevious && !configuration.previousValidUntil().isAfter(configuration.now())) {
            throw new IllegalArgumentException("Previous HMAC key is already expired");
        }
        return new HmacKeyRing(configuration);
    }

    public Optional<String> secretFor(String keyId, Instant now) {
        if (current.keyId().equals(keyId)) {
            return Optional.of(current.secret());
        }
        if (previous != null && previous.keyId().equals(keyId)
                && previous.validUntil().isAfter(now)) {
            return Optional.of(previous.secret());
        }
        return Optional.empty();
    }

    public String currentKeyId() {
        return current.keyId();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Configuration(String currentKeyId, String currentSecret,
            String previousKeyId, String previousSecret, Instant previousValidUntil, Instant now) {
        public Configuration {
            if (now == null) {
                now = Instant.now();
            }
        }
    }

    private record Key(String keyId, String secret, Instant validUntil) {
    }
}



