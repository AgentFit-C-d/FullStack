package com.agentfit.coreapi.catalog.discovery;

import java.io.PrintStream;
import java.nio.file.Path;

/** Operator-only command. Downloads pinned source files, never activates a Catalog release. */
public final class SkillsShCandidateDownloadCommand {
    private SkillsShCandidateDownloadCommand() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length < 3 || args.length > 4
            || args.length == 4 && !"--public".equals(args[3])) {
            err.println("USAGE: SkillsShCandidateDownloadCommand <snapshot> <external-id>"
                + " <new-review-directory> [--public]");
            return 2;
        }
        try {
            SkillsShCandidateCollector collector = args.length == 4
                ? SkillsShCandidateCollector.publicExperimental()
                : SkillsShCandidateCollector.production(() -> System.getenv("VERCEL_OIDC_TOKEN"));
            var downloaded = SkillsShCandidateFileStager.stage(Path.of(args[0]), args[1],
                Path.of(args[2]), collector);
            out.printf("SKILL_FILES_STAGED hash=%s files=%d; CATALOG_NOT_APPROVED%n",
                downloaded.contentHash(), downloaded.files().size());
            if (args.length == 4) out.println("EXPERIMENTAL_PUBLIC_API");
            return 0;
        } catch (IllegalArgumentException | IllegalStateException
                 | SkillsShCandidateCollector.DiscoveryException exception) {
            err.println("SKILL_FILES_NOT_STAGED: " + exception.getMessage());
            return 1;
        }
    }
}
