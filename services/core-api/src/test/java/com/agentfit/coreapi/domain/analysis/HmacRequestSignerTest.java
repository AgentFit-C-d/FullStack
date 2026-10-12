package com.agentfit.coreapi.domain.analysis;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class HmacRequestSignerTest {
    @Test
    void signsExactCanonicalContextWithPaddingFreeBase64Url() {
        Instant now = Instant.parse("2026-10-08T00:00:00Z");
        HmacKeyRing ring = HmacKeyRing.from(new HmacKeyRing.Configuration("current", "secret", null, null, null, now));
        HmacRequestSigner signer = new HmacRequestSigner(ring);

        HmacRequestSigner.SignedRequest signed = signer.sign("POST", "/internal/v1/analysis", "1700000000",
            "r-1", 1L, "v1", "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals("current", signed.headers().get("X-AgentFit-Key-Id"));
        assertEquals("1700000000", signed.headers().get("X-AgentFit-Timestamp"));
        assertEquals("ED86yzH5Yb24R8fV1sIOTCPAUuw16zoA4NOSMzje2jY", signed.headers().get("X-AgentFit-Signature"));
    }
}



