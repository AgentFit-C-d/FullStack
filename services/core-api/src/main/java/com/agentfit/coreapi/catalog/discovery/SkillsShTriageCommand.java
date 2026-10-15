package com.agentfit.coreapi.catalog.discovery;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;

/** Local operator triage; never approves a Catalog release or enables recommendations. */
public final class SkillsShTriageCommand {
    private SkillsShTriageCommand() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err, Clock.systemUTC()));
    }

    static int run(String[] args, PrintStream out, PrintStream err, Clock clock) {
        if (args == null || !(args.length == 3 && "list".equals(args[0])
            || args.length == 5 && ("shortlist".equals(args[0]) || "reject".equals(args[0])))
            || args[1] == null || args[1].isBlank() || args[2] == null || args[2].isBlank()) {
            usage(err);
            return 2;
        }
        try {
            Path snapshot = Path.of(args[1]);
            Path decisions = Path.of(args[2]);
            if (!Files.isRegularFile(snapshot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("candidate snapshot unavailable");
            }
            if ("list".equals(args[0])) {
                var items = SkillsShReviewQueue.classify(snapshot,
                    SkillsShReviewDecisionStore.load(decisions));
                for (var item : items) {
                    out.printf("CANDIDATE externalId=%s hash=%s status=%s%n",
                        item.candidate().externalId(), item.candidate().contentHash(), item.status());
                }
                out.printf("TRIAGE_LISTED count=%d; CATALOG_NOT_APPROVED%n", items.size());
            } else {
                if (args[3] == null || args[4] == null) {
                    usage(err);
                    return 2;
                }
                var disposition = "shortlist".equals(args[0])
                    ? SkillsShReviewQueue.Disposition.SHORTLISTED
                    : SkillsShReviewQueue.Disposition.REJECTED;
                var recorded = SkillsShReviewDecisionStore.record(snapshot, decisions,
                    args[3], disposition, args[4], clock);
                out.printf("TRIAGE_RECORDED externalId=%s hash=%s disposition=%s; CATALOG_NOT_APPROVED%n",
                    recorded.externalId(), recorded.reviewedHash(), recorded.disposition());
            }
            return 0;
        } catch (SkillsShCandidateSnapshot.InvalidSnapshotException
            | SkillsShReviewDecisionStore.InvalidDecisionsException
            | IllegalArgumentException exception) {
            err.println("TRIAGE_FAILED: " + exception.getClass().getSimpleName());
            return 1;
        }
    }

    private static void usage(PrintStream err) {
        err.println("USAGE: SkillsShTriageCommand list <snapshot> <decisions>"
            + " | shortlist|reject <snapshot> <decisions> <external-id> <reviewer-id>");
    }
}
