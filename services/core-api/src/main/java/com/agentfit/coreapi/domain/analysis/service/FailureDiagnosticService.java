package com.agentfit.coreapi.domain.analysis.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Optional, encrypted diagnostics for the final analysis failure only. Raw input is never accepted here. */
@Service
public final class FailureDiagnosticService {
    private static final int MAX_BYTES = 64 * 1024;
    private static final int TAG_BITS = 128;
    private static final Duration RETENTION = Duration.ofDays(7);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final String keyId;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public FailureDiagnosticService(JdbcTemplate jdbc,
            @Value("${agentfit.analysis.failure-diagnostic.enabled:false}") boolean enabled,
            @Value("${agentfit.analysis.failure-diagnostic.key-id:}") String keyId,
            @Value("${agentfit.analysis.failure-diagnostic.key:}") String encodedKey) {
        this(jdbc, Clock.systemUTC(), enabled, keyId, encodedKey);
    }

    public FailureDiagnosticService(JdbcTemplate jdbc, Clock clock, boolean enabled, String keyId, String encodedKey) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = enabled;
        this.keyId = keyId == null ? "" : keyId;
        this.key = decodeKey(encodedKey);
    }

    /** Returns false when disabled, misconfigured, or already recorded for this attempt. */
    public boolean recordFinalFailure(UUID projectId, UUID attemptId, byte[] receivedResponse, Instant receivedAt) {
        if (!enabled || projectId == null || attemptId == null || receivedResponse == null
                || key.length == 0 || keyId.isBlank()) return false;
        Instant received = receivedAt == null ? Instant.now(clock) : receivedAt;
        byte[] bounded = receivedResponse.length <= MAX_BYTES ? receivedResponse.clone()
            : java.util.Arrays.copyOf(receivedResponse, MAX_BYTES);
        boolean truncated = receivedResponse.length > MAX_BYTES;
        byte[] ciphertext = encrypt(bounded);
        try {
            jdbc.update("""
                INSERT INTO analysis_failure_diagnostic
                    (id, project_id, attempt_id, received_at, expires_at, ciphertext, key_id,
                     byte_size, truncated, redacted, redaction_policy_version,
                     truncation_policy_version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, attemptId, received, received.plus(RETENTION),
                ciphertext, keyId, bounded.length, truncated, true, "diagnostic-v1", "64k-v1", received);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${agentfit.analysis.failure-diagnostic.cleanup-interval-seconds:3600}000")
    public int purgeExpired() {
        return jdbc.update("DELETE FROM analysis_failure_diagnostic WHERE expires_at <= ?", Instant.now(clock));
    }

    static byte[] encryptForTest(byte[] plain, byte[] key, SecureRandom random) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return ByteBuffer.allocate(nonce.length + cipher.getOutputSize(plain.length))
                .put(nonce).put(cipher.doFinal(plain)).array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("diagnostic encryption failed", ex);
        }
    }

    private byte[] encrypt(byte[] plain) { return encryptForTest(plain, key, random); }

    private static byte[] decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) return new byte[0];
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) return new byte[0];
            return decoded;
        } catch (IllegalArgumentException ex) { return new byte[0]; }
    }
}
