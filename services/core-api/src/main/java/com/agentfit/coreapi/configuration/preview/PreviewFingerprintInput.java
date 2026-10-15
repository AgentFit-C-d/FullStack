package com.agentfit.coreapi.configuration.preview;

import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Transient inputs needed to regenerate the exact Preview; not a public request DTO. */
public record PreviewFingerprintInput(PreviewBasis basis, String recommendationId,
                                      List<String> selectedToolIds,
                                      List<PermissionSelection> policies,
                                      String generatorVersion,
                                      List<PreviewInputFile> generatedFiles,
                                      ExistingState existingState,
                                      List<PreviewInputFile> providedFiles) {
    public PreviewFingerprintInput {
        selectedToolIds = snapshot(selectedToolIds);
        policies = snapshot(policies);
        generatedFiles = snapshot(generatedFiles);
        providedFiles = snapshot(providedFiles);
    }

    private static <T> List<T> snapshot(List<T> values) {
        return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
