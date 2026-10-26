package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintResult;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.selection.ConfigurationSelectionValidator;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.RecommendationPreviewGate;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure B Preview path; caller must provide A's owner-checked basis, target, and stored recommendation. */
public final class CatalogPreviewAssembler {
    private CatalogPreviewAssembler() {}

    public record Assembled(PreviewFingerprintResult preview,
                            PreviewCapabilityCoverage.Result coverage) {}

    private record Prepared(PreviewFingerprintInput input, ParsedCatalog catalog) {}

    public static PreviewFingerprintResult assemble(Path releaseDirectory, String approvedCatalogHash,
                                                     StoredRecommendationState storedRecommendation,
                                                     PreviewBasis currentBasis,
                                                     EnvironmentTarget currentTarget,
                                                     CatalogPreviewRequest request) {
        return assemble(releaseDirectory, approvedCatalogHash, storedRecommendation, currentBasis,
            currentTarget, request, Map.of());
    }

    public static PreviewFingerprintResult assemble(Path releaseDirectory, String approvedCatalogHash,
                                                     StoredRecommendationState storedRecommendation,
                                                     PreviewBasis currentBasis,
                                                     EnvironmentTarget currentTarget,
                                                     CatalogPreviewRequest request,
                                                     Map<String, String> trustedInstalledVersions) {
        return PreviewFingerprint.compute(prepare(releaseDirectory, approvedCatalogHash,
            storedRecommendation, currentBasis, currentTarget, request, trustedInstalledVersions));
    }

    public static Assembled assembleWithCoverage(Path releaseDirectory, String approvedCatalogHash,
                                                  StoredRecommendationState storedRecommendation,
                                                  PreviewBasis currentBasis,
                                                  EnvironmentTarget currentTarget,
                                                  CatalogPreviewRequest request,
                                                  Map<String, String> trustedInstalledVersions,
                                                  Set<String> requiredCapabilityKeys,
                                                  List<PreviewCreationService.StoredItemEvidence> storedItems) {
        Prepared prepared = prepareDetailed(releaseDirectory, approvedCatalogHash,
            storedRecommendation, currentBasis, currentTarget, request, trustedInstalledVersions);
        requireStoredEvidence(prepared.catalog(), storedRecommendation, currentTarget,
            requiredCapabilityKeys, storedItems, trustedInstalledVersions);
        var coverage = PreviewCapabilityCoverage.evaluate(prepared.catalog().release(), currentTarget,
            requiredCapabilityKeys, request.selectedToolKeys(), trustedInstalledVersions);
        return new Assembled(PreviewFingerprint.compute(prepared.input()), coverage);
    }

    static PreviewFingerprintInput prepareVerified(Path releaseDirectory, String approvedCatalogHash,
        StoredRecommendationState storedRecommendation, PreviewBasis currentBasis,
        EnvironmentTarget currentTarget, CatalogPreviewRequest request,
        Map<String, String> trustedInstalledVersions,
        List<PreviewCreationService.StoredItemEvidence> storedItems) {
        Prepared prepared = prepareDetailed(releaseDirectory, approvedCatalogHash,
            storedRecommendation, currentBasis, currentTarget, request, trustedInstalledVersions);
        requireStoredEvidence(prepared.catalog(), storedRecommendation, currentTarget,
            Set.of(), storedItems, trustedInstalledVersions);
        return prepared.input();
    }

    private static void requireStoredEvidence(ParsedCatalog catalog,
        StoredRecommendationState recommendation, EnvironmentTarget target,
        Set<String> requiredCapabilityKeys,
        List<PreviewCreationService.StoredItemEvidence> storedItems,
        Map<String, String> trustedInstalledVersions) {
        if (storedItems == null) {
            throw new InvalidAssemblyException("stored recommendation evidence missing");
        }
        Set<String> defaultKeys = storedItems.stream()
            .filter(PreviewCreationService.StoredItemEvidence::required)
            .map(PreviewCreationService.StoredItemEvidence::toolKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (defaultKeys.isEmpty()) {
            throw new InvalidAssemblyException("stored recommendation has no default tool");
        }
        var decision = new RecommendationDecision(recommendation.status(),
            recommendation.toolKeys(), List.of(), List.of());
        var details = CatalogRecommendationDetails.project(catalog, decision,
            requiredCapabilityKeys, target);
        if (details.size() != storedItems.size()) {
            throw new InvalidAssemblyException("stored recommendation differs from approved Catalog");
        }
        Set<String> defaultCoverage = new HashSet<>();
        for (String key : defaultKeys) {
            var tool = catalog.release().tools().get(key);
            if (tool == null || tool.support().stream().noneMatch(row -> row.verifiedFor(target))) {
                throw new InvalidAssemblyException("stored default tool is unsupported");
            }
            defaultCoverage.addAll(tool.capabilityKeys());
        }
        for (var installed : trustedInstalledVersions.entrySet()) {
            var tool = catalog.release().tools().get(installed.getKey());
            if (tool != null && tool.version().equals(installed.getValue())
                && tool.support().stream().anyMatch(row -> row.verifiedFor(target))) {
                defaultCoverage.addAll(tool.capabilityKeys());
            }
        }
        if (!defaultCoverage.containsAll(requiredCapabilityKeys)) {
            throw new InvalidAssemblyException("stored default tools do not cover required capabilities");
        }
        for (int index = 0; index < details.size(); index++) {
            var detail = details.get(index);
            var saved = storedItems.get(index);
            if (!detail.toolKey().equals(saved.toolKey())
                || !detail.catalogVersion().equals(saved.toolVersion())
                || !detail.supportKey().equals(saved.supportKey())) {
                throw new InvalidAssemblyException("stored recommendation differs from approved Catalog");
            }
        }
    }

    static PreviewFingerprintInput prepare(Path releaseDirectory, String approvedCatalogHash,
                                           StoredRecommendationState storedRecommendation,
                                           PreviewBasis currentBasis,
                                           EnvironmentTarget currentTarget,
                                           CatalogPreviewRequest request) {
        return prepare(releaseDirectory, approvedCatalogHash, storedRecommendation, currentBasis,
            currentTarget, request, Map.of());
    }

    static PreviewFingerprintInput prepare(Path releaseDirectory, String approvedCatalogHash,
                                           StoredRecommendationState storedRecommendation,
                                           PreviewBasis currentBasis,
                                           EnvironmentTarget currentTarget,
                                           CatalogPreviewRequest request,
                                           Map<String, String> trustedInstalledVersions) {
        return prepareDetailed(releaseDirectory, approvedCatalogHash, storedRecommendation,
            currentBasis, currentTarget, request, trustedInstalledVersions).input();
    }

    private static Prepared prepareDetailed(Path releaseDirectory, String approvedCatalogHash,
                                            StoredRecommendationState storedRecommendation,
                                            PreviewBasis currentBasis,
                                            EnvironmentTarget currentTarget,
                                            CatalogPreviewRequest request,
                                            Map<String, String> trustedInstalledVersions) {
        if (request == null || request.basis() == null || request.selectedToolKeys() == null
            || request.selectedToolKeys().isEmpty()
            || request.selectedToolKeys().stream().anyMatch(key -> key == null || key.isBlank())
            || request.permissionSelections() == null || request.providedFiles() == null
            || trustedInstalledVersions == null) {
            throw new InvalidAssemblyException("incomplete Preview request");
        }
        if (currentTarget == null || !currentTarget.complete() || request.target() == null
            || !request.target().complete() || !currentTarget.equals(request.target())) {
            throw new InvalidAssemblyException("Preview target differs from current environment");
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
            .validate(selected, request.target(), parsed.permissionMappings(),
                request.permissionSelections(), trustedInstalledVersions);
        // v1 static output is scoped to a tool, not an individual permission mapping.
        // A denied optional mapping cannot safely be omitted from those bytes yet.
        if (validatedPolicies.stream().anyMatch(policy -> policy.policy() == PermissionPolicy.DENY)) {
            throw new InvalidAssemblyException("denied permission cannot be represented by static output");
        }
        List<PreviewInputFile> generated = CatalogStaticTemplateRenderer.render(bundle,
            approvedCatalogHash, selected);
        PreviewAssemblyLimits.validateGenerated(generated);
        var input = new PreviewFingerprintInput(request.basis(),
            request.recommendationId(), request.selectedToolKeys(), validatedPolicies,
            request.generatorVersion(), generated, request.existingState(), request.providedFiles());
        return new Prepared(input, parsed);
    }

    public static final class InvalidAssemblyException extends IllegalArgumentException {
        public InvalidAssemblyException(String message) { super(message); }
    }
}
