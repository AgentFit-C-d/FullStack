package com.agentfit.coreapi.domain.analysis.service;


import com.agentfit.coreapi.domain.analysis.client.*;
import com.agentfit.coreapi.domain.analysis.config.*;
import com.agentfit.coreapi.domain.analysis.entity.*;
import com.agentfit.coreapi.domain.analysis.service.*;
import com.agentfit.coreapi.domain.project.service.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacRequestSigner {
    private final HmacKeyRing keyRing;

    public HmacRequestSigner(HmacKeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public SignedRequest sign(String method, String path, String timestamp, String requestId,
            long generation, String contractVersion, byte[] rawBody) {
        String keyId = currentKeyId();
        String secret = keyRing.secretFor(keyId, Instant.now())
            .orElseThrow(() -> new IllegalStateException("current HMAC key is unavailable"));
        String canonical = canonicalString(method, path, timestamp, requestId, generation, contractVersion, rawBody);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
            return new SignedRequest(keyId, timestamp, signature);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign internal request", ex);
        }
    }

    public static String canonicalString(String method, String path, String timestamp, String requestId,
            long generation, String contractVersion, byte[] rawBody) {
        return String.join("\n", method.toUpperCase(), path, timestamp, requestId,
            Long.toString(generation), contractVersion,
            HexFormat.of().formatHex(sha256(rawBody)));
    }

    private static byte[] sha256(byte[] rawBody) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(rawBody);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String currentKeyId() {
        return keyRing.currentKeyId();
    }

    public record SignedRequest(String keyId, String timestamp, String signature) {
        public Map<String, String> headers() {
            return Map.of("X-AgentFit-Key-Id", keyId, "X-AgentFit-Timestamp", timestamp,
                "X-AgentFit-Signature", signature);
        }
    }
}



