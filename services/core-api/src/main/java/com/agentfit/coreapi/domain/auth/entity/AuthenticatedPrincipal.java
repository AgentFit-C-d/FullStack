package com.agentfit.coreapi.domain.auth.entity;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.time.Instant;
import java.util.UUID;

/** The only user information exposed to protected application services. */
public record AuthenticatedPrincipal(long userId, String name, Instant sessionExpiresAt, UUID userUuid) {
    public AuthenticatedPrincipal(long userId, String name, Instant sessionExpiresAt) {
        this(userId, name, sessionExpiresAt, null);
    }

    public AuthenticatedPrincipal(UserAccount account, Instant sessionExpiresAt) {
        this(account.id(), account.name(), sessionExpiresAt, account.uuid());
    }
}



