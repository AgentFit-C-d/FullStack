package com.agentfit.coreapi.domain.analysis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.agentfit.coreapi.domain.analysis.service.FailureDiagnosticService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FailureDiagnosticServiceTest {
    @Test
    void disabledByDefaultDoesNotWriteRawFailure() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new FailureDiagnosticService(jdbc,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), false, "", "");
        assertFalse(service.recordFinalFailure(UUID.randomUUID(), UUID.randomUUID(),
            "provider-raw".getBytes(), Instant.now()));
        verifyNoInteractions(jdbc);
    }

    @Test
    void enabledDiagnosticIsEncryptedBoundedAndRetainedForSevenDays() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        String encoded = Base64.getEncoder().encodeToString(new byte[32]);
        var received = Instant.parse("2026-01-01T00:00:00Z");
        var service = new FailureDiagnosticService(jdbc,
            Clock.fixed(received, ZoneOffset.UTC), true, "diagnostic-key-1", encoded);
        byte[] response = new byte[70_000];
        assertTrue(service.recordFinalFailure(UUID.randomUUID(), UUID.randomUUID(), response, received));
        var args = mockingDetails(jdbc).getInvocations().stream().findFirst().orElseThrow().getArguments();
        assertTrue((Boolean) args[9]);
        assertEquals(received.plusSeconds(7 * 24 * 3600), args[5]);
        assertFalse(java.util.Arrays.equals(response, (byte[]) args[6]));
        service.purgeExpired();
        verify(jdbc).update(startsWith("DELETE FROM analysis_failure_diagnostic"), any(Object[].class));
    }
}
