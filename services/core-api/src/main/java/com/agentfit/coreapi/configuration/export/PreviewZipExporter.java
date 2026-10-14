package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Regenerates only Previewed bytes after a caller has checked approval and current basis. */
final class PreviewZipExporter {
    private static final int MAX_FILES = 20;
    private static final long MAX_UNCOMPRESSED_BYTES = 1_048_576;

    private PreviewZipExporter() {}

    public static byte[] export(PreviewFingerprintInput input, String expectedFingerprint) {
        if (expectedFingerprint == null || !expectedFingerprint.matches("[0-9a-f]{64}")) {
            throw new StalePreviewException("invalid Preview fingerprint");
        }
        PreviewFingerprintResult regenerated = PreviewFingerprint.compute(input);
        if (!MessageDigest.isEqual(expectedFingerprint.getBytes(StandardCharsets.US_ASCII),
            regenerated.fingerprint().getBytes(StandardCharsets.US_ASCII))) {
            throw new StalePreviewException("Preview fingerprint changed");
        }
        if (regenerated.files().size() > MAX_FILES) throw new InvalidExportException("too many files");

        List<PathBytes> entries = new ArrayList<>();
        long total = 0;
        for (PreviewFile file : regenerated.files()) {
            byte[] bytes = file.content().getBytes(StandardCharsets.UTF_8);
            total += bytes.length;
            if (total > MAX_UNCOMPRESSED_BYTES || !sha256(bytes).equals(file.afterHash())) {
                throw new InvalidExportException("file size or hash mismatch");
            }
            entries.add(new PathBytes(file.relativePath(), bytes));
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (PathBytes entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.path());
                zipEntry.setTime(0L);
                zip.putNextEntry(zipEntry);
                zip.write(entry.bytes());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new InvalidExportException("ZIP generation failed", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record PathBytes(String path, byte[] bytes) {}

    public static final class StalePreviewException extends IllegalArgumentException {
        public StalePreviewException(String message) { super(message); }
    }

    public static final class InvalidExportException extends IllegalStateException {
        public InvalidExportException(String message) { super(message); }
        public InvalidExportException(String message, Throwable cause) { super(message, cause); }
    }
}
