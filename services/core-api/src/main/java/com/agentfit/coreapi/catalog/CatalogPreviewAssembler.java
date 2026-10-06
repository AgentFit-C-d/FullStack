package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.recommendation.selection.ConfigurationSelectionValidator;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.RecommendationPreviewGate;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure B Preview path; caller must provide A's owner-checked current basis and stored recommendation. */
public final class CatalogPreviewAssembler {
    private CatalogPreviewAssembler() {}

    public static PreviewFingerprintResult assemble(Path releaseDirectory, String approvedCatalogHash,
                                                     StoredRecommendationState storedRecommendation,
                                                     PreviewBasis currentBasis, CatalogPreviewRequest request) {
        if (request == null || request.basis() == null || request.selectedToolKeys() == null
            || request.selectedToolKeys().isEmpty()
            || request.selectedToolKeys().stream().anyMatch(key -> key == null || key.isBlank())
            || request.permissionSelections() == null || request.providedFiles() == null) {
            throw new InvalidAssemblyException("incomplete Preview request");
        }
        PreviewAssemblyLimits.validateRequest(request);
        Set<String> selected = new HashSet<>(request.selectedToolKeys());
        if (selected.size() != request.selectedToolKeys().size()) {
            throw new InvalidAssemblyException("duplicate selected tool");
        }
        RecommendationPreviewGate.requireEligible(storedRecommendation, currentBasis,
            request.recommendationId(), request.basis(), request.selectedToolKeys());

        VerifiedCatalogBundle bundle = CatalogBundleLoader.load(releaseDirectory);
        if (approvedCatalogHash == null || !approvedCatalogHash.matches("[0-9a-f]{64}")
            || !MessageDigest.isEqual(bundle.catalogHash().getBytes(StandardCharsets.US_ASCII),
                approvedCatalogHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new CatalogBundleLoader.CatalogUnavailableException("catalog hash is not approved");
        }
        if (!Objects.equals(request.basis().catalogReleaseId(), bundle.releaseId())
            || !Objects.equals(request.basis().catalogHash(), bundle.catalogHash())) {
            throw new InvalidAssemblyException("Preview basis Catalog changed");
        }

        ParsedCatalog parsed = CatalogSemanticParser.parse(bundle);
        List<PermissionSelection> validatedPolicies = new ConfigurationSelectionValidator(parsed.release())
            .validate(selected, request.target(), parsed.permissionMappings(), request.permissionSelections());
        List<PreviewInputFile> generated = CatalogStaticTemplateRenderer.render(bundle,
            approvedCatalogHash, selected);
        PreviewAssemblyLimits.validateGenerated(generated);
        return PreviewFingerprint.compute(new PreviewFingerprintInput(request.basis(),
            request.recommendationId(), request.selectedToolKeys(), validatedPolicies,
            request.generatorVersion(), generated, request.existingState(), request.providedFiles()));
    }

    public static final class InvalidAssemblyException extends IllegalArgumentException {
        public InvalidAssemblyException(String message) { super(message); }
    }
}
