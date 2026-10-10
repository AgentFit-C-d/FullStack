package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogCandidateValidatorCommandTest {
    @TempDir Path directory;

    @Test
    void reportsCandidateHashOnlyAfterIntegrityAndSchemaValidation() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var result = run(directory);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("CANDIDATE_SCHEMA_VALID"));
        assertTrue(result.stdout().contains(hash));
        assertTrue(result.stdout().contains("RELEASE_NOT_READY"));
        assertTrue(result.stdout().contains("INDEPENDENT_APPROVAL_REQUIRED"));
        assertFalse(result.stdout().contains("APPROVED"));
    }

    @Test
    void reportsUnreviewedRealCandidateWithoutImplyingReleaseApproval() {
        Path candidate = Path.of("..", "..", "catalog-candidates",
            "playwright-mcp-0.0.83-windows-claude-2.1.270");
        var result = run(candidate);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("CANDIDATE_SCHEMA_VALID"));
        assertTrue(result.stdout().contains("RELEASE_NOT_READY"));
        assertTrue(result.stdout().contains("SUPPORT_NOT_RUN"));
        assertTrue(result.stdout().contains("PERMISSION_REVIEW_REQUIRED"));
        assertTrue(result.stdout().contains("OUTPUT_REVIEW_REQUIRED"));
        assertTrue(result.stdout().contains("INDEPENDENT_APPROVAL_REQUIRED"));
        assertFalse(result.stdout().contains("RELEASE_READY"));
    }

    @Test
    void flagsAbsentCombinationReviewWhenCandidateContainsMultipleTools() throws Exception {
        SyntheticCatalogBundle.writeWithFallback(directory, "literal config\n");
        var result = run(directory);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("COMBINATION_REVIEW_REQUIRED"));
    }

    @Test
    void reportsFailedSupportCheckAsReleaseBlocker() throws Exception {
        SyntheticCatalogBundle.writeWithFailedSupport(directory);
        var result = run(directory);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("CANDIDATE_SCHEMA_VALID"));
        assertTrue(result.stdout().contains("SUPPORT_FAILED"));
        assertTrue(result.stdout().contains("RELEASE_NOT_READY"));
    }

    @Test
    void rejectsChangedBytesAndAHashValidButSemanticallyEmptyRelease() throws Exception {
        SyntheticCatalogBundle.write(directory);
        Files.writeString(directory.resolve("tools.json"), "{}", StandardCharsets.UTF_8);
        var changed = run(directory);
        assertEquals(1, changed.exitCode());
        assertTrue(changed.stderr().contains("CANDIDATE_INVALID"));
        assertTrue(changed.stdout().isEmpty());

        String fileHash = "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a";
        String catalogHash = "38e92a97ab9126a133c5f3bb85ebfdd2d55e43c82515d887b6ded60b6698d3a5";
        String[] names = {"capabilities.json", "client-capabilities.json", "permissions.json",
            "relations.json", "support-matrix.json", "tools.json"};
        var entries = new java.util.ArrayList<String>();
        for (String name : names) {
            Files.writeString(directory.resolve(name), "{}", StandardCharsets.UTF_8);
            entries.add("{\"path\":\"" + name + "\",\"sha256\":\"" + fileHash + "\"}");
        }
        Files.writeString(directory.resolve("manifest.json"), "{\"schemaVersion\":1,"
            + "\"releaseId\":\"release-test-1\",\"catalogHash\":\"" + catalogHash
            + "\",\"files\":[" + String.join(",", entries) + "]}", StandardCharsets.UTF_8);
        var invalidSchema = run(directory);
        assertEquals(1, invalidSchema.exitCode());
        assertTrue(invalidSchema.stderr().contains("CANDIDATE_INVALID"));
        assertTrue(invalidSchema.stdout().isEmpty());
    }

    @Test
    void rejectsVerifiedToolWithoutRenderableOutput() throws Exception {
        SyntheticCatalogBundle.write(directory, false);
        var result = run(directory);
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("CANDIDATE_INVALID"));
        assertTrue(result.stdout().isEmpty());
    }

    @Test
    void rejectsVerifiedToolWhoseOutputExceedsPreviewBudget() throws Exception {
        SyntheticCatalogBundle.write(directory, true, "x".repeat(100_001));
        var result = run(directory);
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("CANDIDATE_INVALID"));
        assertTrue(result.stdout().isEmpty());
    }

    @Test
    void rejectsReviewedCombinationWhoseCombinedOutputExceedsPreviewBudget() throws Exception {
        SyntheticCatalogBundle.writeWithOversizedCombination(directory);
        var result = run(directory);
        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("CANDIDATE_INVALID"));
        assertTrue(result.stdout().isEmpty());
    }

    private Result run(Path release) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int exitCode = CatalogCandidateValidatorCommand.run(new String[] {release.toString()},
            new PrintStream(stdout), new PrintStream(stderr));
        return new Result(exitCode, stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String stdout, String stderr) {}
}
