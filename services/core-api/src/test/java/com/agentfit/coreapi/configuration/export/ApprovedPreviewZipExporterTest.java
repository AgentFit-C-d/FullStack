package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class ApprovedPreviewZipExporterTest {
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final Instant PREVIEW_EXPIRY = NOW.plusSeconds(600);

    private PreviewBasis basis() {
        return new PreviewBasis("project-1", 2, "profile-1", 3,
            "event-1", 4, 5, 6, "release-1", "a".repeat(64));
    }

    private PreviewFingerprintInput input(String content) {
        return new PreviewFingerprintInput(basis(), "rec-1", List.of("tool-1"), List.of(),
            "static-v1", List.of(new PreviewInputFile("main", "config/main.txt", content)),
            ExistingState.UNKNOWN, List.of());
    }

    private StoredPreviewState preview(String fingerprint) {
        return new StoredPreviewState("preview-1", basis(), fingerprint,
            NOW.minusSeconds(120), PREVIEW_EXPIRY);
    }

    private StoredApprovalState approval(String fingerprint) {
        return new StoredApprovalState("approval-1", "preview-1", fingerprint,
            NOW.minusSeconds(60), PREVIEW_EXPIRY);
    }

    @Test
    void exportsOnlyWhenStoredApprovalAndRegeneratedBytesMatch() throws IOException {
        PreviewFingerprintInput input = input("safe config\n");
        String fingerprint = PreviewFingerprint.compute(input).fingerprint();
        byte[] zip = ApprovedPreviewZipExporter.export(preview(fingerprint), approval(fingerprint),
            basis(), "approval-1", "preview-1", fingerprint, input, NOW);
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(zip),
            StandardCharsets.UTF_8)) {
            assertEquals("config/main.txt", archive.getNextEntry().getName());
            assertArrayEquals("safe config\n".getBytes(StandardCharsets.UTF_8), archive.readAllBytes());
            assertNull(archive.getNextEntry());
        }
    }

    @Test
    void rejectsWrongApprovalOrPreviewIdentity() {
        PreviewFingerprintInput input = input("safe config\n");
        String fingerprint = PreviewFingerprint.compute(input).fingerprint();
        assertThrows(ApprovedPreviewZipExporter.InvalidApprovalException.class,
            () -> ApprovedPreviewZipExporter.export(preview(fingerprint), approval(fingerprint),
                basis(), "other-approval", "preview-1", fingerprint, input, NOW));
        assertThrows(ApprovedPreviewZipExporter.InvalidApprovalException.class,
            () -> ApprovedPreviewZipExporter.export(preview(fingerprint), approval(fingerprint),
                basis(), "approval-1", "other-preview", fingerprint, input, NOW));
    }

    @Test
    void rejectsChangedBytesBasisOrApprovalFingerprint() {
        PreviewFingerprintInput input = input("safe config\n");
        String fingerprint = PreviewFingerprint.compute(input).fingerprint();
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> ApprovedPreviewZipExporter.export(preview(fingerprint), approval(fingerprint),
                basis(), "approval-1", "preview-1", fingerprint, input("changed\n"), NOW));
        PreviewBasis changed = new PreviewBasis("project-1", 3, "profile-1", 3,
            "event-1", 4, 5, 6, "release-1", "a".repeat(64));
        assertThrows(PreviewFreshnessGate.StalePreviewException.class,
            () -> ApprovedPreviewZipExporter.export(preview(fingerprint), approval(fingerprint),
                changed, "approval-1", "preview-1", fingerprint, input, NOW));
        assertThrows(ApprovedPreviewZipExporter.InvalidApprovalException.class,
            () -> ApprovedPreviewZipExporter.export(preview(fingerprint), approval("f".repeat(64)),
                basis(), "approval-1", "preview-1", fingerprint, input, NOW));
    }

    @Test
    void rejectsExpiredApprovalAndLifetimeBeyondPreview() {
        PreviewFingerprintInput input = input("safe config\n");
        String fingerprint = PreviewFingerprint.compute(input).fingerprint();
        StoredApprovalState expired = new StoredApprovalState("approval-1", "preview-1",
            fingerprint, NOW.minusSeconds(60), NOW);
        assertThrows(ApprovedPreviewZipExporter.ExpiredApprovalException.class,
            () -> ApprovedPreviewZipExporter.export(preview(fingerprint), expired, basis(),
                "approval-1", "preview-1", fingerprint, input, NOW));
        StoredApprovalState tooLong = new StoredApprovalState("approval-1", "preview-1",
            fingerprint, NOW.minusSeconds(60), PREVIEW_EXPIRY.plusSeconds(1));
        assertThrows(ApprovedPreviewZipExporter.InvalidApprovalException.class,
            () -> ApprovedPreviewZipExporter.export(preview(fingerprint), tooLong, basis(),
                "approval-1", "preview-1", fingerprint, input, NOW));
    }

    @Test
    void rejectsApprovalRecordedBeforePreviewCreation() {
        PreviewFingerprintInput input = input("safe config\n");
        String fingerprint = PreviewFingerprint.compute(input).fingerprint();
        StoredPreviewState preview = new StoredPreviewState("preview-1", basis(), fingerprint,
            NOW.minusSeconds(30), PREVIEW_EXPIRY);
        StoredApprovalState earlierApproval = new StoredApprovalState("approval-1", "preview-1",
            fingerprint, NOW.minusSeconds(60), PREVIEW_EXPIRY);
        assertThrows(ApprovedPreviewZipExporter.InvalidApprovalException.class,
            () -> ApprovedPreviewZipExporter.export(preview, earlierApproval, basis(),
                "approval-1", "preview-1", fingerprint, input, NOW));
    }
}
