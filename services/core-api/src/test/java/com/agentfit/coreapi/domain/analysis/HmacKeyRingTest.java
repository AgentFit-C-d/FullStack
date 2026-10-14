package com.agentfit.coreapi.domain.analysis;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class HmacKeyRingTest {
    @Test
    void currentKeyPairIsRequired() {
        assertThrows(IllegalArgumentException.class,
            () -> HmacKeyRing.from(new HmacKeyRing.Configuration(null, null, null, null, null, Instant.now())));
    }

    @Test
    void previousKeyMustContainAllThreeValues() {
        assertThrows(IllegalArgumentException.class,
            () -> HmacKeyRing.from(new HmacKeyRing.Configuration("current", "secret", "old", "old-secret", null,
                Instant.now())));
    }

    @Test
    void expiredPreviousKeyIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> HmacKeyRing.from(new HmacKeyRing.Configuration("current", "secret", "old", "old-secret",
                Instant.now().minusSeconds(1), Instant.now())));
    }

    @Test
    void keyLookupReturnsOnlyValidCurrentOrPreviousKey() {
        Instant now = Instant.parse("2026-10-08T00:00:00Z");
        HmacKeyRing ring = HmacKeyRing.from(new HmacKeyRing.Configuration("current", "secret", "old", "old-secret",
            now.plusSeconds(60), now));

        assertEquals(Optional.of("secret"), ring.secretFor("current", now));
        assertEquals(Optional.of("old-secret"), ring.secretFor("old", now));
        assertEquals(Optional.empty(), ring.secretFor("old", now.plusSeconds(61)));
        assertEquals(Optional.empty(), ring.secretFor("unknown", now));
    }
}



