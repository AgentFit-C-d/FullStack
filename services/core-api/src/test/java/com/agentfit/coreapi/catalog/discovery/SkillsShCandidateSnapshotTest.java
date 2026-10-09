package com.agentfit.coreapi.catalog.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsShCandidateSnapshotTest {
    private Path directory;

    @BeforeEach void setUp() throws Exception {
        directory = Files.createTempDirectory(Path.of("target"), "skills-candidates-");
    }

    @AfterEach void tearDown() throws Exception {
        Files.deleteIfExists(directory.resolve("skills-sh-candidates.json"));
        Files.deleteIfExists(directory);
    }

    private SkillCandidate candidate(String hash, String name) {
        return new SkillCandidate("owner/repo/example", name, "owner/repo",
            "https://skills.sh/owner/repo/example", hash,
            Instant.parse("2026-10-09T00:00:00Z"));
    }

    @Test
    void savesNewAndChangedCandidatesWithoutDeletingAbsentSearchHits() throws Exception {
        Path snapshot = directory.resolve("skills-sh-candidates.json");
        SkillCandidate first = candidate("a".repeat(64), "Example");
        assertEquals(List.of(first), SkillsShCandidateSnapshot.refresh(snapshot, List.of(first)));
        assertEquals(List.of(first), SkillsShCandidateSnapshot.load(snapshot));
        assertEquals(List.of(), SkillsShCandidateSnapshot.refresh(snapshot, List.of(first)));
        assertEquals(List.of(), SkillsShCandidateSnapshot.refresh(snapshot, List.of()));
        assertEquals(List.of(first), SkillsShCandidateSnapshot.load(snapshot));

        SkillCandidate changed = candidate("b".repeat(64), "Example");
        assertEquals(List.of(changed), SkillsShCandidateSnapshot.refresh(snapshot, List.of(changed)));
        assertEquals(List.of(changed), SkillsShCandidateSnapshot.load(snapshot));
    }

    @Test
    void rejectsCorruptSnapshotWithoutOverwritingIt() throws Exception {
        Path snapshot = directory.resolve("skills-sh-candidates.json");
        Files.writeString(snapshot, "{\"candidates\":not-json}");
        String original = Files.readString(snapshot);
        assertThrows(SkillsShCandidateSnapshot.InvalidSnapshotException.class,
            () -> SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate("a".repeat(64), "Example"))));
        assertEquals(original, Files.readString(snapshot));
    }
}
