package com.agentfit.coreapi.catalog.discovery;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Local operator command for staging discovery candidates; it cannot approve a Catalog. */
public final class SkillsShCandidateRefreshCommand {
    private SkillsShCandidateRefreshCommand() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err, () -> System.getenv("VERCEL_OIDC_TOKEN")));
    }

    static int run(String[] args, PrintStream out, PrintStream err, Supplier<String> tokenSupplier) {
        if (!validArguments(args)) {
            usage(err);
            return 2;
        }
        String token = tokenSupplier.get();
        if (token == null || token.isBlank()) {
            err.println("CANDIDATE_REFRESH_FAILED: credential unavailable");
            return 1;
        }
        try {
            return run(args, out, err, SkillsShCandidateCollector.production(tokenSupplier));
        } catch (UncheckedIOException exception) {
            err.println("CANDIDATE_REFRESH_FAILED: HTTP client unavailable");
            return 1;
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err,
                   SkillsShCandidateCollector collector) {
        if (!validArguments(args)) {
            usage(err);
            return 2;
        }
        int limit = Integer.parseInt(args[1]);
        try {
            var changed = new SkillsShCandidateRefresh(collector)
                .refresh(args[0], limit, Path.of(args[2]));
            out.printf("CANDIDATES_STAGED changed=%d; CATALOG_NOT_APPROVED%n", changed.size());
            return 0;
        } catch (SkillsShCandidateCollector.DiscoveryException
            | SkillsShCandidateSnapshot.InvalidSnapshotException
            | IllegalArgumentException exception) {
            err.println("CANDIDATE_REFRESH_FAILED: " + exception.getClass().getSimpleName());
            return 1;
        }
    }

    private static boolean validArguments(String[] args) {
        if (args == null || args.length != 3 || args[0] == null || args[1] == null
            || args[2] == null || args[0].strip().length() < 2 || args[0].length() > 100
            || args[2].isBlank()) return false;
        try {
            int limit = Integer.parseInt(args[1]);
            return limit >= 1 && limit <= 20;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static void usage(PrintStream err) {
        err.println("USAGE: SkillsShCandidateRefreshCommand <query> <limit:1-20> <snapshot-path>");
    }
}
