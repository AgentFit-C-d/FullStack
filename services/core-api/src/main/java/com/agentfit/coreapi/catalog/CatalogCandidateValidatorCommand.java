package com.agentfit.coreapi.catalog;

import java.io.PrintStream;
import java.nio.file.Path;

/** Developer-only candidate check; never approves or activates a Catalog. */
public final class CatalogCandidateValidatorCommand {
    private CatalogCandidateValidatorCommand() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length != 1 || args[0] == null || args[0].isBlank()) {
            err.println("USAGE: CatalogCandidateValidatorCommand <release-directory>");
            return 2;
        }
        try {
            ParsedCatalog candidate = CatalogSemanticParser.load(Path.of(args[0]));
            out.printf("CANDIDATE_SCHEMA_VALID releaseId=%s catalogHash=%s tools=%d%n",
                candidate.release().releaseId(), candidate.catalogHash(), candidate.release().tools().size());
            return 0;
        } catch (CatalogBundleLoader.CatalogUnavailableException | IllegalArgumentException exception) {
            err.println("CANDIDATE_INVALID: " + exception.getMessage());
            return 1;
        }
    }
}
