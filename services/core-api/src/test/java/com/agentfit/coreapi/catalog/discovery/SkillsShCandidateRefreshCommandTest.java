package com.agentfit.coreapi.catalog.discovery;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsShCandidateRefreshCommandTest {
    @Test
    void refreshesCandidateSnapshotWithoutActivatingCatalog() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "skills-command-");
        Path snapshot = directory.resolve("candidates.json");
        try {
            var collector = collector((uri, token) -> {
                String body = uri.getPath().endsWith("/search")
                    ? "{\"data\":[{\"id\":\"owner/repo/example\",\"name\":\"Example\","
                        + "\"source\":\"owner/repo\",\"url\":\"https://skills.sh/owner/repo/example\"}]}"
                    : "{\"id\":\"owner/repo/example\",\"source\":\"owner/repo\","
                        + "\"hash\":\"" + "a".repeat(64) + "\"}";
                return response(200, body);
            });
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();

            assertEquals(0, SkillsShCandidateRefreshCommand.run(
                new String[] {"example", "5", snapshot.toString()},
                new PrintStream(out), new PrintStream(err), collector));
            assertEquals(1, SkillsShCandidateSnapshot.load(snapshot).size());
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("CANDIDATES_STAGED"));
            assertFalse(out.toString(StandardCharsets.UTF_8).contains("CATALOG_APPROVED"));
            assertEquals("", err.toString(StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void explicitPublicModeStagesCandidatesWithExperimentalLabel() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "skills-public-command-");
        Path snapshot = directory.resolve("candidates.json");
        try {
            var collector = SkillsShCandidateCollector.publicExperimental((uri, token) -> {
                assertEquals("", token);
                return uri.getPath().equals("/api/search")
                    ? response(200, "{\"skills\":[{\"id\":\"owner/repo/example\",\"name\":\"Example\",\"source\":\"owner/repo\"}]}")
                    : response(200, "{\"hash\":\"" + "a".repeat(64) + "\",\"files\":[]}");
            }, URI.create("https://skills.sh"), Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC));
            var out = new ByteArrayOutputStream();
            assertEquals(0, SkillsShCandidateRefreshCommand.run(
                new String[] {"react", "2", snapshot.toString(), "--public"},
                new PrintStream(out), new PrintStream(new ByteArrayOutputStream()), collector));
            assertEquals(1, SkillsShCandidateSnapshot.load(snapshot).size());
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("EXPERIMENTAL_PUBLIC_API"));
        } finally {
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void providerFailurePreservesExistingSnapshotAndDoesNotPrintCredential() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "skills-command-fail-");
        Path snapshot = directory.resolve("candidates.json");
        try {
            Files.writeString(snapshot, "{\"schemaVersion\":1,\"candidates\":[]}");
            byte[] before = Files.readAllBytes(snapshot);
            var collector = collector((uri, token) -> response(503, "{}"));
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();

            assertEquals(1, SkillsShCandidateRefreshCommand.run(
                new String[] {"example", "5", snapshot.toString()},
                new PrintStream(out), new PrintStream(err), collector));
            assertArrayEquals(before, Files.readAllBytes(snapshot));
            assertTrue(err.toString(StandardCharsets.UTF_8).contains("CANDIDATE_REFRESH_FAILED"));
            assertFalse(err.toString(StandardCharsets.UTF_8).contains("secret-test-token"));
            assertEquals("", out.toString(StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void rejectsInvalidArgumentsBeforeSearch() {
        var collector = collector((uri, token) -> { throw new AssertionError("network called"); });
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        assertEquals(2, SkillsShCandidateRefreshCommand.run(new String[] {"example", "21", "x.json"},
            new PrintStream(out), new PrintStream(err), collector));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("USAGE"));
    }

    @Test
    void missingCredentialFailsBeforeProductionHttpClientIsCreated() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        Supplier<String> missingToken = () -> null;
        assertEquals(1, SkillsShCandidateRefreshCommand.run(
            new String[] {"example", "5", "unused.json"},
            new PrintStream(out), new PrintStream(err), missingToken));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("CANDIDATE_REFRESH_FAILED"));
        assertEquals("", out.toString(StandardCharsets.UTF_8));
    }

    private static SkillsShCandidateCollector collector(SkillsShCandidateCollector.Transport transport) {
        return new SkillsShCandidateCollector(transport, URI.create("https://skills.sh"),
            () -> "secret-test-token", Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC));
    }

    private static SkillsShCandidateCollector.Response response(int status, String body) {
        return new SkillsShCandidateCollector.Response(status, body.getBytes(StandardCharsets.UTF_8));
    }
}
