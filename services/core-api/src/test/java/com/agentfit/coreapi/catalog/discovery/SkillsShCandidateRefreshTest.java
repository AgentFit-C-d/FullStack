package com.agentfit.coreapi.catalog.discovery;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsShCandidateRefreshTest {
    @Test
    void stagesOnlySuccessfulSearchAndPreservesSnapshotAfterProviderFailure() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "skills-refresh-");
        Path snapshot = directory.resolve("candidates.json");
        String hash = "a".repeat(64);
        SkillsShCandidateCollector.Transport valid = (uri, token) -> {
            String body = uri.getPath().endsWith("/search")
                ? "{\"data\":[{\"id\":\"owner/repo/example\",\"name\":\"Example\","
                    + "\"source\":\"owner/repo\",\"url\":\"https://skills.sh/owner/repo/example\"}]}"
                : "{\"id\":\"owner/repo/example\",\"source\":\"owner/repo\",\"hash\":\"" + hash + "\"}";
            return new SkillsShCandidateCollector.Response(200, body.getBytes(StandardCharsets.UTF_8));
        };
        Clock clock = Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC);
        try {
            var collector = new SkillsShCandidateCollector(valid, URI.create("https://skills.sh"),
                () -> "test-token", clock);
            var refresh = new SkillsShCandidateRefresh(collector);
            List<SkillCandidate> changed = refresh.refresh("example", 5, snapshot);
            assertEquals(1, changed.size());
            assertEquals(changed, SkillsShCandidateSnapshot.load(snapshot));
            assertEquals(List.of(), refresh.refresh("example", 5, snapshot));

            byte[] before = Files.readAllBytes(snapshot);
            SkillsShCandidateCollector.Transport failure = (uri, token) ->
                new SkillsShCandidateCollector.Response(503, "{}".getBytes(StandardCharsets.UTF_8));
            var failing = new SkillsShCandidateRefresh(new SkillsShCandidateCollector(failure,
                URI.create("https://skills.sh"), () -> "test-token", clock));
            assertThrows(SkillsShCandidateCollector.DiscoveryException.class,
                () -> failing.refresh("example", 5, snapshot));
            assertArrayEquals(before, Files.readAllBytes(snapshot));
        } finally {
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(directory);
        }
    }
}
