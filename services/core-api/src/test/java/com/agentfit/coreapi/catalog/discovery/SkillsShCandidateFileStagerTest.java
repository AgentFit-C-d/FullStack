package com.agentfit.coreapi.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillsShCandidateFileStagerTest {
    private static final String HASH = "a".repeat(64);
    @TempDir Path directory;

    @Test
    void stagesOnlyThePinnedPackageWithoutChangingTheSnapshot() throws Exception {
        Path snapshot = directory.resolve("snapshot.json");
        SkillCandidate candidate = candidate();
        SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate));
        Path target = directory.resolve("review-files");

        var packageInfo = SkillsShCandidateFileStager.stage(snapshot,
            candidate.externalId(), target, collector(HASH, "SKILL.md"));

        assertEquals(HASH, packageInfo.contentHash());
        assertEquals("---\nname: example\n---\n",
            Files.readString(target.resolve("SKILL.md")));
        assertEquals("Guide", Files.readString(target.resolve("references/guide.md")));
        assertEquals(List.of(candidate), SkillsShCandidateSnapshot.load(snapshot));
        assertThrows(IllegalArgumentException.class, () -> SkillsShCandidateFileStager.stage(
            snapshot, candidate.externalId(), target, collector(HASH, "SKILL.md")));
    }

    @Test
    void changedHashOrTraversalLeavesNoPartialTarget() {
        Path snapshot = directory.resolve("snapshot.json");
        SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate()));
        Path target = directory.resolve("review-files");

        assertThrows(SkillsShCandidateCollector.DiscoveryException.class,
            () -> SkillsShCandidateFileStager.stage(snapshot,
                candidate().externalId(), target, collector("b".repeat(64), "SKILL.md")));
        assertThrows(SkillsShCandidateCollector.DiscoveryException.class,
            () -> SkillsShCandidateFileStager.stage(snapshot,
                candidate().externalId(), target, collector(HASH, "../SKILL.md")));
        assertEquals(false, Files.exists(target));
    }

    private static SkillCandidate candidate() {
        return new SkillCandidate("owner/repo/example", "Example", "owner/repo",
            "https://skills.sh/owner/repo/example", HASH,
            Instant.parse("2026-10-25T00:00:00Z"));
    }

    private static SkillsShCandidateCollector collector(String hash, String firstPath) {
        SkillsShCandidateCollector.Transport transport = (uri, token) -> {
            String body = "{\"id\":\"owner/repo/example\",\"source\":\"owner/repo\","
                + "\"hash\":\"" + hash + "\",\"files\":["
                + "{\"path\":\"" + firstPath + "\","
                + "\"contents\":\"---\\nname: example\\n---\\n\"},"
                + "{\"path\":\"references/guide.md\",\"contents\":\"Guide\"}]}";
            return new SkillsShCandidateCollector.Response(200,
                body.getBytes(StandardCharsets.UTF_8));
        };
        return new SkillsShCandidateCollector(transport, URI.create("https://skills.sh"),
            () -> "token", Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC));
    }
}
