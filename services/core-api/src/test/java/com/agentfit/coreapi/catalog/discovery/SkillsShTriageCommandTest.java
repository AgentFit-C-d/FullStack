package com.agentfit.coreapi.catalog.discovery;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsShTriageCommandTest {
    @Test
    void missingSnapshotIsAnErrorInsteadOfAnEmptyReviewQueue() throws Exception {
        Path dir = Files.createTempDirectory(Path.of("target"), "skills-triage-missing-");
        try {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            assertEquals(1, SkillsShTriageCommand.run(
                new String[] {"list", dir.resolve("missing.json").toString(),
                    dir.resolve("decisions.json").toString()},
                new PrintStream(out), new PrintStream(err), Clock.systemUTC()));
            assertEquals("", out.toString(StandardCharsets.UTF_8));
            assertTrue(err.toString(StandardCharsets.UTF_8).contains("TRIAGE_FAILED"));
        } finally {
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void listsPendingAndChangedCandidatesWithoutApprovingThem() throws Exception {
        Path dir = Files.createTempDirectory(Path.of("target"), "skills-triage-command-");
        Path snapshot = dir.resolve("candidates.json");
        Path decisions = dir.resolve("decisions.json");
        var clock = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);
        try {
            SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate("a".repeat(64))));
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            assertEquals(0, SkillsShTriageCommand.run(
                new String[] {"list", snapshot.toString(), decisions.toString()},
                new PrintStream(out), new PrintStream(err), clock));
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("status=PENDING"));

            out.reset();
            assertEquals(0, SkillsShTriageCommand.run(
                new String[] {"shortlist", snapshot.toString(), decisions.toString(),
                    "owner/repo/example", "reviewer-1"},
                new PrintStream(out), new PrintStream(err), clock));
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("CATALOG_NOT_APPROVED"));
            assertEquals(1, SkillsShReviewDecisionStore.load(decisions).size());

            SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate("b".repeat(64))));
            out.reset();
            assertEquals(0, SkillsShTriageCommand.run(
                new String[] {"list", snapshot.toString(), decisions.toString()},
                new PrintStream(out), new PrintStream(err), clock));
            assertTrue(out.toString(StandardCharsets.UTF_8).contains("status=CHANGED"));
            assertEquals("", err.toString(StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(decisions);
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(dir);
        }
    }

    private static SkillCandidate candidate(String hash) {
        return new SkillCandidate("owner/repo/example", "Example", "owner/repo",
            "https://skills.sh/owner/repo/example", hash, Instant.parse("2026-10-12T00:00:00Z"));
    }
}
