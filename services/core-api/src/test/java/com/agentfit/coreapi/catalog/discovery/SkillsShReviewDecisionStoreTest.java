package com.agentfit.coreapi.catalog.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsShReviewDecisionStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");

    @Test
    void recordsHumanTriageAgainstCurrentCandidateAndReopensAfterHashChange() throws Exception {
        Path dir = Files.createTempDirectory(Path.of("target"), "skills-decisions-");
        Path snapshot = dir.resolve("candidates.json");
        Path decisions = dir.resolve("decisions.json");
        try {
            SkillCandidate candidate = candidate("a".repeat(64));
            SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate));
            var recorded = SkillsShReviewDecisionStore.record(snapshot, decisions, candidate.externalId(),
                SkillsShReviewQueue.Disposition.SHORTLISTED, "reviewer-1", Clock.fixed(NOW, ZoneOffset.UTC));
            assertEquals(candidate.contentHash(), recorded.reviewedHash());
            assertEquals(candidate.name(), recorded.reviewedName());
            assertEquals("reviewer-1", recorded.reviewerId());
            assertEquals(SkillsShReviewQueue.Status.SHORTLISTED,
                SkillsShReviewQueue.classify(snapshot, SkillsShReviewDecisionStore.load(decisions))
                    .getFirst().status());

            SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate("b".repeat(64))));
            assertEquals(SkillsShReviewQueue.Status.CHANGED,
                SkillsShReviewQueue.classify(snapshot, SkillsShReviewDecisionStore.load(decisions))
                    .getFirst().status());
            assertEquals(SkillsShReviewQueue.Disposition.SHORTLISTED,
                SkillsShReviewDecisionStore.load(decisions).getFirst().disposition());
        } finally {
            Files.deleteIfExists(decisions);
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void rejectsUnknownCandidateAndCorruptDecisionsWithoutReplacingFile() throws Exception {
        Path dir = Files.createTempDirectory(Path.of("target"), "skills-decisions-bad-");
        Path snapshot = dir.resolve("candidates.json");
        Path decisions = dir.resolve("decisions.json");
        try {
            SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate("a".repeat(64))));
            assertThrows(IllegalArgumentException.class,
                () -> SkillsShReviewDecisionStore.record(snapshot, decisions, "owner/repo/missing",
                    SkillsShReviewQueue.Disposition.REJECTED, "reviewer-1", Clock.fixed(NOW, ZoneOffset.UTC)));
            assertFalse(Files.exists(decisions));

            Files.writeString(decisions, "{\"schemaVersion\":1,\"decisions\":[],\"approved\":true}");
            byte[] before = Files.readAllBytes(decisions);
            assertThrows(SkillsShReviewDecisionStore.InvalidDecisionsException.class,
                () -> SkillsShReviewDecisionStore.record(snapshot, decisions, "owner/repo/example",
                    SkillsShReviewQueue.Disposition.REJECTED, "reviewer-1", Clock.fixed(NOW, ZoneOffset.UTC)));
            assertArrayEquals(before, Files.readAllBytes(decisions));
        } finally {
            Files.deleteIfExists(decisions);
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void replacesExistingTriageInsteadOfDuplicatingIt() throws Exception {
        Path dir = Files.createTempDirectory(Path.of("target"), "skills-decisions-replace-");
        Path snapshot = dir.resolve("candidates.json");
        Path decisions = dir.resolve("decisions.json");
        try {
            SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate("a".repeat(64))));
            var clock = Clock.fixed(NOW, ZoneOffset.UTC);
            SkillsShReviewDecisionStore.record(snapshot, decisions, "owner/repo/example",
                SkillsShReviewQueue.Disposition.SHORTLISTED, "reviewer-1", clock);
            SkillsShReviewDecisionStore.record(snapshot, decisions, "owner/repo/example",
                SkillsShReviewQueue.Disposition.REJECTED, "reviewer-2", clock);
            var stored = SkillsShReviewDecisionStore.load(decisions);
            assertEquals(1, stored.size());
            assertEquals(SkillsShReviewQueue.Disposition.REJECTED, stored.getFirst().disposition());
            assertEquals("reviewer-2", stored.getFirst().reviewerId());
        } finally {
            Files.deleteIfExists(decisions);
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(dir);
        }
    }

    private static SkillCandidate candidate(String hash) {
        return new SkillCandidate("owner/repo/example", "Example", "owner/repo",
            "https://skills.sh/owner/repo/example", hash, NOW);
    }
}
