package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Transient Preview inputs; generated files are always supplied by the approved Catalog. */
public record CatalogPreviewRequest(PreviewBasis basis, String recommendationId,
                                    EnvironmentTarget target, List<String> selectedToolKeys,
                                    List<PermissionSelection> permissionSelections,
                                    ExistingState existingState, List<PreviewInputFile> providedFiles,
                                    String generatorVersion) {
    public CatalogPreviewRequest {
        selectedToolKeys = snapshot(selectedToolKeys);
        permissionSelections = snapshot(permissionSelections);
        providedFiles = snapshot(providedFiles);
    }

    private static <T> List<T> snapshot(List<T> values) {
        return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
