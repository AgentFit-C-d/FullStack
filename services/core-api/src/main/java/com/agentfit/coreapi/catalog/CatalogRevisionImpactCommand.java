package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.VerifiedCombination;
import java.nio.file.Path;
import java.util.Comparator;

/** Local review aid. Reporting an impact never approves or activates a Catalog release. */
public final class CatalogRevisionImpactCommand {
    private CatalogRevisionImpactCommand() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: <previous-candidate> <next-candidate>");
        }
        var before = CatalogBundleLoader.load(Path.of(args[0]));
        var after = CatalogBundleLoader.load(Path.of(args[1]));
        var impact = CatalogRevisionImpact.compare(before, after);
        System.out.println("CATALOG_REVISION_CHANGED " + impact.revisionChanged());
        impact.toolsNeedingReview().stream().sorted()
            .forEach(key -> System.out.println("TOOL_REVIEW_REQUIRED " + key));
        impact.combinationsNeedingReview().stream()
            .sorted(Comparator.comparing(CatalogRevisionImpactCommand::combinationKey))
            .forEach(combination -> System.out.println("COMBINATION_REVIEW_REQUIRED "
                + combinationKey(combination)));
        System.out.println("CATALOG_NOT_APPROVED");
    }

    private static String combinationKey(VerifiedCombination combination) {
        var target = combination.target();
        return target.osFamily() + "/" + target.clientId() + "/" + target.clientVersion()
            + ":" + String.join(",", combination.toolKeys().stream().sorted().toList());
    }
}
