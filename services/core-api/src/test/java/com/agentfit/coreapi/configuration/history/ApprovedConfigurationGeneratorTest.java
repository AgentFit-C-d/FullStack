package com.agentfit.coreapi.configuration.history;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.export.ApprovedPreviewZipExporter;
import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ApprovedConfigurationGeneratorTest {
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PreviewBasis BASIS = new PreviewBasis("project-1", 2,
        "profile-1", 3, "event-1", 4, 5, 6, "catalog-1", "a".repeat(64));

    private PreviewFingerprintInput input(String content) {
        return new PreviewFingerprintInput(BASIS, "rec-1", List.of("tool-1"), List.of(),
            "static-v1", List.of(new PreviewInputFile("main", "config/main.txt", content)),
            ExistingState.UNKNOWN, List.of());
    }

    private ApprovedConfigurationGenerator.Generated generate(PreviewFingerprintInput input,
                                                               String fingerprint, Instant expiry) {
        return ApprovedConfigurationGenerator.generate(
            new StoredPreviewState("preview-1", BASIS, fingerprint, expiry),
            new StoredApprovalState("approval-1", "preview-1", fingerprint,
                NOW.minusSeconds(60), expiry), BASIS,
            "approval-1", "preview-1", fingerprint, input, "generation-1", CLOCK);
    }

    @Test
    void returnsExactZipAndContentFreeGeneratedHistoryTogether() throws IOException {
        PreviewFingerprintInput input = input("safe config\n");
        String fingerprint = PreviewFingerprint.compute(input).fingerprint();
        var generated = generate(input, fingerprint, NOW.plusSeconds(600));

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(generated.zipBytes()),
            StandardCharsets.UTF_8)) {
            assertEquals("config/main.txt", zip.getNextEntry().getName());
            assertArrayEquals("safe config\n".getBytes(StandardCharsets.UTF_8), zip.readAllBytes());
            assertNull(zip.getNextEntry());
        }
        assertEquals(ConfigurationGenerationHistory.Status.GENERATED, generated.history().status());
        assertEquals(ConfigurationGenerationHistory.VerificationStatus.NOT_RUN,
            generated.history().verificationStatus());
        assertEquals("generation-1", generated.history().id());
        byte[] mutated = generated.zipBytes();
        mutated[0] ^= 1;
        assertNotEquals(mutated[0], generated.zipBytes()[0]);
    }

    @Test
    void rejectsExpiredApprovalOrChangedPreviewBeforeReturningHistory() {
        PreviewFingerprintInput input = input("safe config\n");
        String fingerprint = PreviewFingerprint.compute(input).fingerprint();
        assertThrows(ApprovedPreviewZipExporter.ExpiredApprovalException.class,
            () -> generate(input, fingerprint, NOW));
        assertThrows(IllegalStateException.class,
            () -> generate(input("changed config\n"), fingerprint, NOW.plusSeconds(600)));
    }

    @Test
    void refusesOversizedInputBeforeProducingGeneratedResult() {
        List<PreviewInputFile> files = IntStream.range(0, 21)
            .mapToObj(i -> new PreviewInputFile("target-" + i, "config/" + i + ".txt", "value\n"))
            .toList();
        PreviewFingerprintInput input = new PreviewFingerprintInput(BASIS, "rec-1",
            List.of("tool-1"), List.of(), "static-v1", files, ExistingState.UNKNOWN, List.of());
        assertThrows(PreviewFingerprint.LimitExceededException.class,
            () -> generate(input, "a".repeat(64), NOW.plusSeconds(600)));
    }
}
