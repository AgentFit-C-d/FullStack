package com.agentfit.coreapi.configuration.history;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationGenerationHistoryTest {
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PreviewBasis BASIS = new PreviewBasis("project-1", 2,
        "profile-1", 3, "event-1", 4, 5, 6, "catalog-1", "a".repeat(64));

    private PreviewFingerprintInput input(String path, String content) {
        return new PreviewFingerprintInput(BASIS, "rec-1", List.of("tool-1"), List.of(),
            "static-v1", List.of(new PreviewInputFile("main", path, content)),
            ExistingState.PROVIDED, List.of(new PreviewInputFile("main", path, "old config\n")));
    }

    @Test
    void capturesOnlyMinimumMetadataWithGeneratedAndUnverifiedState() {
        PreviewFingerprintInput input = input("config/main.txt", "new config\n");
        var preview = PreviewFingerprint.compute(input);
        var history = ConfigurationGenerationHistory.capture("generation-1", "preview-1",
            "approval-1", preview.fingerprint(), input, CLOCK);

        assertEquals("generation-1", history.id());
        assertEquals("rec-1", history.recommendationId());
        assertEquals(BASIS, history.basis());
        assertEquals(preview.fingerprint(), history.fingerprint());
        assertEquals(NOW, history.createdAt());
        assertEquals(ConfigurationGenerationHistory.Status.GENERATED, history.status());
        assertEquals(ConfigurationGenerationHistory.VerificationStatus.NOT_RUN, history.verificationStatus());
        assertEquals(ConfigurationGenerationHistory.VerificationSource.NONE, history.verificationSource());
        assertEquals(1, history.files().size());
        assertEquals("config/main.txt", history.files().getFirst().relativePath());
        assertEquals(PreviewFile.Action.UPDATE, history.files().getFirst().action());
        assertEquals(preview.files().getFirst().afterHash(), history.files().getFirst().afterHash());
        assertEquals(ConfigurationGenerationHistory.Validity.CURRENT, history.validity(BASIS));
    }

    @Test
    void marksChangedBasisStaleWithoutInventingDownloadOrInstallationState() {
        PreviewFingerprintInput input = input("config/main.txt", "new config\n");
        var history = ConfigurationGenerationHistory.capture("generation-1", "preview-1",
            "approval-1", PreviewFingerprint.compute(input).fingerprint(), input, CLOCK);
        PreviewBasis changed = new PreviewBasis("project-1", 2, "profile-1", 3,
            "event-1", 4, 5, 7, "catalog-1", "a".repeat(64));
        assertEquals(ConfigurationGenerationHistory.Validity.STALE, history.validity(changed));
        assertThrows(IllegalArgumentException.class, () -> history.validity(null));
        assertEquals(ConfigurationGenerationHistory.Status.GENERATED, history.status());
    }

    @Test
    void rejectsChangedOrInvalidPreviewBeforeCapturingHistory() {
        PreviewFingerprintInput original = input("config/main.txt", "new config\n");
        String expected = PreviewFingerprint.compute(original).fingerprint();
        assertThrows(IllegalArgumentException.class, () -> ConfigurationGenerationHistory.capture(
            "generation-1", "preview-1", "approval-1", expected,
            input("config/main.txt", "changed config\n"), CLOCK));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationGenerationHistory.capture(
            "generation-1", "preview-1", "approval-1", expected,
            input("../unsafe", "new config\n"), CLOCK));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationGenerationHistory.capture(
            " ", "preview-1", "approval-1", expected, original, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> ConfigurationGenerationHistory.capture(
            "generation-1", "preview-1", "approval-1", expected, original, null));
    }
}
