package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.persistence.JdbcConfigurationContextReader;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import java.util.Map;
import java.util.Objects;

/** Rebuilds a saved Preview's warning for final confirmation or download display. */
public final class StoredPreviewCoverage {
    private StoredPreviewCoverage() {}

    public static PreviewCapabilityCoverage.Result evaluate(
        JdbcConfigurationContextReader.Snapshot context, ParsedCatalog catalog,
        EnvironmentTarget currentTarget, Map<String, String> trustedInstalledVersions) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(catalog);
        var recommendation = Objects.requireNonNull(context.recommendation());
        var preview = Objects.requireNonNull(context.preview());
        if (!recommendation.basis().equals(preview.basis())
            || !preview.basis().catalogReleaseId().equals(catalog.release().releaseId())
            || !preview.basis().catalogHash().equals(catalog.catalogHash())
            || context.requiredCapabilityKeys().isEmpty()
            || !recommendation.toolKeys().containsAll(context.selectedToolKeys())) {
            throw new IllegalArgumentException("saved Preview coverage context is inconsistent");
        }
        return PreviewCapabilityCoverage.evaluate(catalog.release(), currentTarget,
            context.requiredCapabilityKeys(), context.selectedToolKeys(),
            trustedInstalledVersions);
    }
}
