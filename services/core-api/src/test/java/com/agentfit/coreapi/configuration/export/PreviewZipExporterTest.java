package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class PreviewZipExporterTest {
    private PreviewBasis basis() {
        return new PreviewBasis("project-1", 7, "profile-1", 3, "event-1", 2,
            0, 5, "release-1", "a".repeat(64));
    }

    private PreviewFingerprintInput input(String config, String original) {
        return new PreviewFingerprintInput(basis(), "rec-1", List.of("doc"), List.of(),
            "generator-1", List.of(
                new PreviewInputFile("client", "settings/config.txt", config),
                new PreviewInputFile("guide", "README.txt", "apply guide\n")),
            ExistingState.PROVIDED,
            List.of(new PreviewInputFile("client", "settings/config.txt", original)));
    }

    @Test
    void archiveContainsOnlyExactPreviewPathsAndBytes() throws IOException {
        String content = "\uFEFFhello\r\n";
        PreviewFingerprintInput input = input(content, "old secret only for comparison");
        PreviewFingerprintResult preview = PreviewFingerprint.compute(input);
        byte[] archive = PreviewZipExporter.export(input, preview.fingerprint());
        Map<String, byte[]> extracted = unzip(archive);
        assertEquals(2, extracted.size());
        assertArrayEquals(content.getBytes(StandardCharsets.UTF_8),
            extracted.get("settings/config.txt"));
        assertArrayEquals("apply guide\n".getBytes(StandardCharsets.UTF_8),
            extracted.get("README.txt"));
        assertFalse(extracted.values().stream().map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .anyMatch(text -> text.contains("old secret only for comparison")));
    }

    @Test
    void changedGeneratedOrProvidedBytesCannotReuseApprovedFingerprint() {
        PreviewFingerprintInput original = input("hello", "old");
        String fingerprint = PreviewFingerprint.compute(original).fingerprint();
        assertThrows(PreviewZipExporter.StalePreviewException.class,
            () -> PreviewZipExporter.export(input("hello\n", "old"), fingerprint));
        assertThrows(PreviewZipExporter.StalePreviewException.class,
            () -> PreviewZipExporter.export(input("hello", "older"), fingerprint));
        assertThrows(PreviewZipExporter.StalePreviewException.class,
            () -> PreviewZipExporter.export(original, "f".repeat(64)));
        assertThrows(PreviewZipExporter.StalePreviewException.class,
            () -> PreviewZipExporter.export(original, "invalid"));
    }

    @Test
    void changedBasisCannotReuseApprovedFingerprint() {
        PreviewFingerprintInput original = input("hello", "old");
        String fingerprint = PreviewFingerprint.compute(original).fingerprint();
        PreviewFingerprintInput changed = new PreviewFingerprintInput(
            new PreviewBasis("project-1", 8, "profile-1", 3, "event-1", 2,
                0, 5, "release-1", "a".repeat(64)), original.recommendationId(),
            original.selectedToolIds(), original.policies(), original.generatorVersion(),
            original.generatedFiles(), original.existingState(), original.providedFiles());
        assertThrows(PreviewZipExporter.StalePreviewException.class,
            () -> PreviewZipExporter.export(changed, fingerprint));
    }

    @Test
    void rejectsOversizedUncompressedOutput() {
        PreviewFingerprintInput large = new PreviewFingerprintInput(basis(), "rec-1",
            List.of("doc"), List.of(), "generator-1",
            List.of(new PreviewInputFile("client", "settings/config.txt", "a".repeat(1_048_577))),
            ExistingState.UNKNOWN, List.of());
        String fingerprint = PreviewFingerprint.compute(large).fingerprint();
        assertThrows(PreviewZipExporter.InvalidExportException.class,
            () -> PreviewZipExporter.export(large, fingerprint));
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertFalse(entry.isDirectory());
                assertNull(files.put(entry.getName(), zip.readAllBytes()));
                zip.closeEntry();
            }
        }
        return files;
    }
}
