package com.agentfit.coreapi.catalog.discovery;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Projects review state for external Skill candidates; shortlisting never approves Catalog support. */
public final class SkillsShReviewQueue {
    private SkillsShReviewQueue() {}

    public enum Disposition { SHORTLISTED, REJECTED }
    public enum Status { PENDING, SHORTLISTED, REJECTED, CHANGED }

    public record Decision(String externalId, String reviewedHash, String reviewedName,
                           Disposition disposition,
                           String reviewerId, Instant reviewedAt) {
        public Decision {
            if (externalId == null
                || !externalId.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)?")
                || reviewedHash == null || !reviewedHash.matches("[0-9a-f]{64}")
                || reviewedName == null || reviewedName.isBlank() || reviewedName.length() > 200
                || disposition == null || reviewerId == null || reviewerId.isBlank()
                || reviewerId.length() > 128 || reviewedAt == null) {
                throw new IllegalArgumentException("invalid candidate review decision");
            }
        }
    }

    public record Item(SkillCandidate candidate, Status status) {}

    public static List<Item> classify(Path snapshot, List<Decision> decisions) {
        if (decisions == null) throw new IllegalArgumentException("review decisions required");
        Map<String, Decision> byId = new HashMap<>();
        for (Decision decision : decisions) {
            if (decision == null || byId.putIfAbsent(decision.externalId(), decision) != null) {
                throw new IllegalArgumentException("duplicate or invalid review decision");
            }
        }
        List<Item> result = new ArrayList<>();
        for (SkillCandidate candidate : SkillsShCandidateSnapshot.load(snapshot)) {
            Decision decision = byId.remove(candidate.externalId());
            Status status = decision == null ? Status.PENDING
                : !decision.reviewedHash().equals(candidate.contentHash())
                    || !decision.reviewedName().equals(candidate.name()) ? Status.CHANGED
                : decision.disposition() == Disposition.SHORTLISTED ? Status.SHORTLISTED : Status.REJECTED;
            result.add(new Item(candidate, status));
        }
        if (!byId.isEmpty()) {
            throw new IllegalArgumentException("review decision has no candidate");
        }
        return List.copyOf(result);
    }
}
