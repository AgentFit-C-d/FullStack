package com.agentfit.coreapi.domain.analysis.config;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HmacConfiguration {
    @Bean
    HmacKeyRing hmacKeyRing(
            @Value("${agentfit.hmac.current-key-id:}") String currentKeyId,
            @Value("${agentfit.hmac.current-secret:}") String currentSecret,
            @Value("${agentfit.hmac.previous-key-id:}") String previousKeyId,
            @Value("${agentfit.hmac.previous-secret:}") String previousSecret,
            @Value("${agentfit.hmac.previous-valid-until:}") String previousValidUntil) {
        Instant expiry = previousValidUntil == null || previousValidUntil.isBlank()
            ? null : Instant.parse(previousValidUntil);
        return HmacKeyRing.from(new HmacKeyRing.Configuration(
            emptyToNull(currentKeyId), emptyToNull(currentSecret), emptyToNull(previousKeyId),
            emptyToNull(previousSecret), expiry, Instant.now()));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}



