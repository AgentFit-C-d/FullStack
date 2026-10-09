package com.agentfit.coreapi.catalog.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsShReviewQueueTest {
    @Test
    void reopensShortlistedCandidateAfterContentChangeWithoutPromotingIt() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "skills-review-");
        Path snapshot = directory.resolve("candidates.json");
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        SkillCandidate first = candidate("a".repeat(64), now);
        try {
            SkillsShCandidateSnapshot.refresh(snapshot, List.of(first));
            var queue = SkillsShReviewQueue.classify(snapshot, List.of());
            assertEquals(SkillsShReviewQueue.Status.PENDING, queue.getFirst().status());

            var decision = new SkillsShReviewQueue.Decision(first.externalId(), first.contentHash(),
                SkillsShReviewQueue.Disposition.SHORTLISTED, "reviewer-1", now);
            queue = SkillsShReviewQueue.classify(snapshot, List.of(decision));
            assertEquals(SkillsShReviewQueue.Status.SHORTLISTED, queue.getFirst().status());

            SkillCandidate changed = candidate("b".repeat(64), now.plusSeconds(60));
            SkillsShCandidateSnapshot.refresh(snapshot, List.of(changed));
            queue = SkillsShReviewQueue.classify(snapshot, List.of(decision));
            assertEquals(SkillsShReviewQueue.Status.CHANGED, queue.getFirst().status());
            assertEquals(changed, queue.getFirst().candidate());
        } finally {
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void rejectedDecisionIsVisibleAndDuplicateDecisionsFailClosed() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "skills-review-");
        Path snapshot = directory.resolve("candidates.json");
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        SkillCandidate candidate = candidate("a".repeat(64), now);
        try {
            SkillsShCandidateSnapshot.refresh(snapshot, List.of(candidate));
            var decision = new SkillsShReviewQueue.Decision(candidate.externalId(), candidate.contentHash(),
                SkillsShReviewQueue.Disposition.REJECTED, "reviewer-1", now);
            assertEquals(SkillsShReviewQueue.Status.REJECTED,
                SkillsShReviewQueue.classify(snapshot, List.of(decision)).getFirst().status());
            assertThrows(IllegalArgumentException.class,
                () -> SkillsShReviewQueue.classify(snapshot, List.of(decision, decision)));
            var orphan = new SkillsShReviewQueue.Decision("owner/repo/missing", candidate.contentHash(),
                SkillsShReviewQueue.Disposition.SHORTLISTED, "reviewer-1", now);
            assertThrows(IllegalArgumentException.class,
                () -> SkillsShReviewQueue.classify(snapshot, List.of(orphan)));
        } finally {
            Files.deleteIfExists(snapshot);
            Files.deleteIfExists(directory);
        }
    }

    private SkillCandidate candidate(String hash, Instant observedAt) {
        return new SkillCandidate("owner/repo/example", "Example", "owner/repo",
            "https://skills.sh/owner/repo/example", hash, observedAt);
    }
}
