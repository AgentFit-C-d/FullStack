package com.agentfit.coreapi.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.entity.UserAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidIdentityCompatibilityTest {
    @Test
    void authenticatedAIdentityCarriesTheCanonicalUuidWithoutChangingBStrings() {
        UUID id = UUID.randomUUID();
        UserAccount account = new UserAccount(id, "owner", 7L, "owner");
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(account, Instant.MAX);

        assertEquals(id, principal.userUuid());
        assertEquals(id, account.uuid());
        assertEquals("owner", principal.name());
    }
}
