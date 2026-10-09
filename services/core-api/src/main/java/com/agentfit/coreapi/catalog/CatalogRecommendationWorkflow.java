package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationEngine;
import com.agentfit.coreapi.recommendation.RecommendationInputAssembler;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Internal B orchestration. Its caller must supply owner-checked A data and an independently approved hash. */
public final class CatalogRecommendationWorkflow {
    private CatalogRecommendationWorkflow() {}

    public record Request(List<AiCapabilityIntake.Claim> claims,
                          List<AiCapabilityIntake.Question> questions,
                          Set<String> allowedSourceFields,
                          boolean hasRelevantPendingConflict,
                          EnvironmentTarget environment,
                          Map<String, String> installedToolVersions) {
        public Request {
            if (claims != null) claims = Collections.unmodifiableList(new ArrayList<>(claims));
            if (questions != null) questions = Collections.unmodifiableList(new ArrayList<>(questions));
            if (allowedSourceFields != null) {
                allowedSourceFields = Collections.unmodifiableSet(new HashSet<>(allowedSourceFields));
            }
            if (installedToolVersions != null) {
                installedToolVersions = Collections.unmodifiableMap(new HashMap<>(installedToolVersions));
            }
        }
    }

    public record Result(String catalogReleaseId, String catalogHash,
                         RecommendationDecision decision,
                         List<AiCapabilityIntake.Claim> capabilities,
                         List<AiCapabilityIntake.Question> clarificationQuestions,
                         List<CatalogRecommendationDetails.Item> items) {
        public Result {
            capabilities = List.copyOf(capabilities);
            clarificationQuestions = List.copyOf(clarificationQuestions);
            items = List.copyOf(items);
        }
    }

    public static Result evaluate(Path releaseDirectory, String approvedCatalogHash, Request request) {
        if (request == null) throw new IllegalArgumentException("recommendation request required");
        VerifiedCatalogBundle bundle = CatalogBundleLoader.load(releaseDirectory);
        ParsedCatalog catalog = CatalogSemanticParser.parse(bundle);
        if (approvedCatalogHash == null || !approvedCatalogHash.matches("[0-9a-f]{64}")
            || !MessageDigest.isEqual(catalog.catalogHash().getBytes(StandardCharsets.US_ASCII),
                approvedCatalogHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new CatalogBundleLoader.CatalogUnavailableException("catalog hash is not approved");
        }
        AiCapabilityIntake.Validated assessment = AiCapabilityIntake.validate(request.claims(),
            request.questions(), request.allowedSourceFields());
        var input = RecommendationInputAssembler.assemble(assessment,
            request.hasRelevantPendingConflict(), request.environment(), request.installedToolVersions());
        boolean[] overBudget = {false};
        RecommendationDecision decision = new RecommendationEngine(catalog.release()).decide(input, additions -> {
            try {
                PreviewAssemblyLimits.validateGenerated(CatalogStaticTemplateRenderer.render(
                    bundle, approvedCatalogHash, Set.copyOf(additions)));
                return true;
            } catch (PreviewAssemblyLimits.LimitExceededException exception) {
                overBudget[0] = true;
                return false;
            }
        });
        if (decision.status() == RecommendationDecision.Status.NO_COMPATIBLE_TOOLS && overBudget[0]) {
            throw new CatalogBundleLoader.CatalogUnavailableException(
                "all matching configurations exceed Preview budget");
        }
        return new Result(catalog.release().releaseId(), catalog.catalogHash(),
            decision, assessment.claims(), assessment.questions(),
            CatalogRecommendationDetails.project(catalog, decision,
                assessment.requiredCapabilityKeys(), request.environment()));
    }
}
