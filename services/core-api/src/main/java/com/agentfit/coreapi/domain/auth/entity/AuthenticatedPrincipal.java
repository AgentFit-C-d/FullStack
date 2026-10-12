package com.agentfit.coreapi.domain.auth.entity;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.time.Instant;

/** The only user information exposed to protected application services. */
public record AuthenticatedPrincipal(long userId, String name, Instant sessionExpiresAt) {
}



