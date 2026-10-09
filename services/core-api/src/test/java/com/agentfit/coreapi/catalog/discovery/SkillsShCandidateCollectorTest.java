package com.agentfit.coreapi.catalog.discovery;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsShCandidateCollectorTest {
    private static final String HASH = "a".repeat(64);
    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile int searchStatus = 200;
    private volatile String searchBody = "";
    private volatile int detailStatus = 200;
    private volatile String detailBody = "";

    private SkillsShCandidateCollector collector(String token) {
        SkillsShCandidateCollector.Transport transport = (uri, bearer) -> {
            if (uri.getRawPath().equals("/api/v1/skills/search")) {
                request.set(uri.getRawPath() + "?" + uri.getRawQuery());
                authorization.set("Bearer " + bearer);
                return new SkillsShCandidateCollector.Response(searchStatus,
                    searchBody.getBytes(StandardCharsets.UTF_8));
            }
            if (uri.getRawPath().equals("/api/v1/skills/owner/repo/example")) {
                return new SkillsShCandidateCollector.Response(detailStatus,
                    detailBody.getBytes(StandardCharsets.UTF_8));
            }
            throw new AssertionError("unexpected request: " + uri);
        };
        return new SkillsShCandidateCollector(transport, URI.create("https://skills.sh"),
            () -> token, Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void discoversSnapshotWithoutPromotingExternalDataToCatalog() {
        searchBody = """
            {"data":[{"id":"owner/repo/example","slug":"example","name":"Example Skill",
              "source":"owner/repo","installs":12,"sourceType":"github",
              "installUrl":"https://github.com/owner/repo",
              "url":"https://skills.sh/owner/repo/example"}],"count":1}
            """;
        detailBody = "{\"id\":\"owner/repo/example\",\"source\":\"owner/repo\","
            + "\"slug\":\"example\",\"hash\":\"" + HASH + "\",\"files\":[]}";
        List<SkillCandidate> candidates = collector("test-token").search("api test", 5);
        assertEquals(1, candidates.size());
        SkillCandidate candidate = candidates.getFirst();
        assertEquals("owner/repo/example", candidate.externalId());
        assertEquals(HASH, candidate.contentHash());
        assertEquals(Instant.parse("2026-10-09T00:00:00Z"), candidate.observedAt());
        assertEquals("/api/v1/skills/search?q=api+test&limit=5", request.get());
        assertEquals("Bearer test-token", authorization.get());
    }

    @Test
    void missingTokenAndProviderFailureCannotBecomeEmptyCandidateSuccess() {
        assertThrows(SkillsShCandidateCollector.DiscoveryException.class,
            () -> collector("").search("test", 5));
        assertNull(request.get());
        searchStatus = 401;
        searchBody = "{\"error\":\"unauthorized\"}";
        assertThrows(SkillsShCandidateCollector.DiscoveryException.class,
            () -> collector("token").search("test", 5));
    }

    @Test
    void rejectsMismatchedDetailIdentityAndInvalidHashes() {
        searchBody = "{\"data\":[{\"id\":\"owner/repo/example\",\"name\":\"Example\","
            + "\"source\":\"owner/repo\",\"url\":\"https://skills.sh/owner/repo/example\"}]}";
        detailBody = "{\"id\":\"owner/repo/other\",\"hash\":\"" + HASH + "\"}";
        assertThrows(SkillsShCandidateCollector.DiscoveryException.class,
            () -> collector("token").search("test", 5));
        detailBody = "{\"id\":\"owner/repo/example\",\"hash\":null}";
        assertThrows(SkillsShCandidateCollector.DiscoveryException.class,
            () -> collector("token").search("test", 5));
    }

    @Test
    void changedHashBecomesReviewCandidateWithoutTreatingMissingSearchHitAsRemoval() {
        SkillCandidate previous = new SkillCandidate("owner/repo/example", "Old name",
            "owner/repo", "https://skills.sh/owner/repo/example", "b".repeat(64),
            Instant.parse("2026-10-08T00:00:00Z"));
        SkillCandidate current = new SkillCandidate("owner/repo/example", "Example Skill",
            "owner/repo", "https://skills.sh/owner/repo/example", HASH,
            Instant.parse("2026-10-09T00:00:00Z"));
        assertEquals(List.of(current), SkillsShCandidateDiff.needingReview(List.of(previous), List.of(current)));
        assertEquals(List.of(), SkillsShCandidateDiff.needingReview(List.of(current), List.of(current)));
        assertEquals(List.of(), SkillsShCandidateDiff.needingReview(List.of(previous), List.of()));
    }

    @Test
    void rejectsCandidateWithInvalidIdentityOrHashBeforeStaging() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
            () -> new SkillCandidate("owner/repo/example", "Example", "owner/repo",
                "https://unrelated.example/example", HASH, now));
        assertThrows(IllegalArgumentException.class,
            () -> new SkillCandidate("owner/repo/example", "Example", "owner/repo",
                "https://skills.sh/owner/repo/example", "unknown", now));
    }
}
