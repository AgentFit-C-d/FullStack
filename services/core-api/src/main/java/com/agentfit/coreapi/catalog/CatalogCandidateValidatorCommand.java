package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.ToolSupport.Check;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            VerifiedCatalogBundle bundle = CatalogBundleLoader.load(Path.of(args[0]));
            ParsedCatalog candidate = CatalogSemanticParser.parse(bundle);
            for (var tool : candidate.release().tools().values()) {
                if (tool.support().stream().noneMatch(row ->
                    row.documentation() == Check.PASS && row.format() == Check.PASS
                        && row.standalone() == Check.PASS)) {
                    continue;
                }
                PreviewAssemblyLimits.validateGenerated(CatalogStaticTemplateRenderer.render(
                    bundle, candidate.catalogHash(), Set.of(tool.key())));
            }
            for (var combination : candidate.release().verifiedCombinations()) {
                PreviewAssemblyLimits.validateGenerated(CatalogStaticTemplateRenderer.render(
                    bundle, candidate.catalogHash(), combination.toolKeys()));
            }
            out.printf("CANDIDATE_SCHEMA_VALID releaseId=%s catalogHash=%s tools=%d%n",
                candidate.release().releaseId(), candidate.catalogHash(), candidate.release().tools().size());
            out.printf("RELEASE_NOT_READY reasons=%s%n",
                String.join(",", readinessReasons(bundle, candidate)));
            return 0;
        } catch (CatalogBundleLoader.CatalogUnavailableException | IllegalArgumentException exception) {
            err.println("CANDIDATE_INVALID: " + exception.getMessage());
            return 1;
        }
    }

    private static List<String> readinessReasons(VerifiedCatalogBundle bundle, ParsedCatalog candidate) {
        List<String> reasons = new ArrayList<>();
        if (candidate.release().tools().values().stream().flatMap(tool -> tool.support().stream())
            .anyMatch(row -> row.documentation() == Check.NOT_RUN || row.format() == Check.NOT_RUN
                || row.standalone() == Check.NOT_RUN)) {
            reasons.add("SUPPORT_NOT_RUN");
        }
        if (candidate.release().tools().keySet().stream().anyMatch(toolKey ->
            candidate.permissionMappings().stream().noneMatch(mapping -> mapping.toolKey().equals(toolKey)))) {
            reasons.add("PERMISSION_REVIEW_REQUIRED");
        }
        if (!bundle.files().containsKey("templates/index.json")) {
            reasons.add("OUTPUT_REVIEW_REQUIRED");
        }
        if (candidate.release().tools().size() > 1
            && candidate.release().verifiedCombinations().isEmpty()) {
            reasons.add("COMBINATION_REVIEW_REQUIRED");
        }
        reasons.add("INDEPENDENT_APPROVAL_REQUIRED");
        return reasons;
    }
}
