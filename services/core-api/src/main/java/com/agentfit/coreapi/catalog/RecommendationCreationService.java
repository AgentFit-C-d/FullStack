package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * B's recommendation creation boundary. Adapters for A authentication/storage and the AI
 * service are intentionally required; this class must not be exposed as an unauthenticated API.
 */
public final class RecommendationCreationService {
    /** A must authenticate the caller, check project ownership, and read one consistent snapshot. */
    public interface OwnerCheckedSnapshotReader {
        OwnerCheckedSnapshot load(String projectId);
    }

    /** The AI adapter receives only A-approved source values, not the owner-checked snapshot. */
    public interface CapabilityAnalyzer {
        CapabilityAssessment analyze(AiInput input);
    }

    public record AiInput(Map<String, String> aiSourceValues) {
        public AiInput {
            aiSourceValues = Map.copyOf(Objects.requireNonNull(aiSourceValues));
        }
    }

    /** Source of an independently approved Catalog hash, never a request parameter. */
    public interface ApprovedCatalogReader {
        ApprovedCatalog current();
    }

    /**
     * A must atomically compare the current versions with the snapshot and persist the result.
     * Throw SnapshotChangedException if any version changed before the save.
     */
    public interface RecommendationStore {
        String saveIfUnchanged(OwnerCheckedSnapshot snapshot, PreviewBasis basis,
                               CatalogRecommendationWorkflow.Result result);
    }

    public record OwnerCheckedSnapshot(String projectId, long projectVersion,
                                       String confirmedProfileId, long confirmedProfileVersion,
                                       String confirmationEventId, long reviewVersion,
                                       long developerVersion, long environmentVersion,
                                       Set<String> allowedSourceFields,
                                       Map<String, String> aiSourceValues,
                                       boolean hasRelevantPendingConflict,
                                       EnvironmentTarget environment,
                                       Map<String, String> installedToolVersions) {
        public OwnerCheckedSnapshot {
            if (blank(projectId) || blank(confirmedProfileId) || blank(confirmationEventId)
                || projectVersion <= 0 || confirmedProfileVersion <= 0 || reviewVersion < 0
                || developerVersion < 0 || environmentVersion < 0) {
                throw new IllegalArgumentException("invalid owner-checked snapshot");
            }
            allowedSourceFields = Set.copyOf(Objects.requireNonNull(allowedSourceFields));
            aiSourceValues = Map.copyOf(Objects.requireNonNull(aiSourceValues));
            if (!allowedSourceFields.containsAll(aiSourceValues.keySet())) {
                throw new IllegalArgumentException("AI source values contain an unapproved field");
            }
            if (installedToolVersions != null && installedToolVersions.entrySet().stream()
                .anyMatch(entry -> blank(entry.getKey())
                    || entry.getValue() != null && entry.getValue().isBlank())) {
                throw new IllegalArgumentException("invalid installed component declaration");
            }
            installedToolVersions = installedToolVersions == null ? null
                : Collections.unmodifiableMap(new HashMap<>(installedToolVersions));
        }

        PreviewBasis basisFor(CatalogRecommendationWorkflow.Result result) {
            return new PreviewBasis(projectId, projectVersion,
                confirmedProfileId, confirmedProfileVersion, confirmationEventId, reviewVersion,
                developerVersion, environmentVersion, result.catalogReleaseId(), result.catalogHash());
        }
    }

    public record CapabilityAssessment(List<AiCapabilityIntake.Claim> claims,
                                       List<AiCapabilityIntake.Question> questions) {
        public CapabilityAssessment {
            claims = List.copyOf(claims);
            questions = List.copyOf(questions);
        }
    }

    public record ApprovedCatalog(Path directory, String approvedHash) {
        public ApprovedCatalog {
            Objects.requireNonNull(directory);
            if (blank(approvedHash)) throw new IllegalArgumentException("approved Catalog hash required");
        }
    }

    /** Client's optimistic version precondition; A rechecks these values in the save transaction. */
    public record ExpectedVersions(long project, long developer, long environment) {
        public ExpectedVersions {
            if (project <= 0 || developer < 0 || environment < 0) {
                throw new IllegalArgumentException("invalid expected recommendation versions");
            }
        }
    }

    public record Created(String recommendationId, PreviewBasis basis,
                          CatalogRecommendationWorkflow.Result result) {}

    public static final class SnapshotChangedException extends RuntimeException {
        public SnapshotChangedException() {
            super("project snapshot changed before recommendation save");
        }
    }

    private final OwnerCheckedSnapshotReader snapshots;
    private final CapabilityAnalyzer analyzer;
    private final ApprovedCatalogReader catalogs;
    private final RecommendationStore store;

    public RecommendationCreationService(OwnerCheckedSnapshotReader snapshots,
                                         CapabilityAnalyzer analyzer,
                                         ApprovedCatalogReader catalogs,
                                         RecommendationStore store) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.analyzer = Objects.requireNonNull(analyzer);
        this.catalogs = Objects.requireNonNull(catalogs);
        this.store = Objects.requireNonNull(store);
    }

    public Created create(String projectId, ExpectedVersions expectedVersions) {
        if (blank(projectId) || expectedVersions == null) {
            throw new IllegalArgumentException("project ID and expected versions required");
        }
        OwnerCheckedSnapshot snapshot = Objects.requireNonNull(snapshots.load(projectId));
        if (!projectId.equals(snapshot.projectId())) {
            throw new IllegalArgumentException("snapshot belongs to another project");
        }
        if (snapshot.projectVersion() != expectedVersions.project()
            || snapshot.developerVersion() != expectedVersions.developer()
            || snapshot.environmentVersion() != expectedVersions.environment()) {
            throw new SnapshotChangedException();
        }
        ApprovedCatalog catalog = Objects.requireNonNull(catalogs.current());
        CapabilityAssessment assessment = Objects.requireNonNull(
            analyzer.analyze(new AiInput(snapshot.aiSourceValues())));
        var result = CatalogRecommendationWorkflow.evaluate(catalog.directory(), catalog.approvedHash(),
            new CatalogRecommendationWorkflow.Request(assessment.claims(), assessment.questions(),
                snapshot.allowedSourceFields(), snapshot.hasRelevantPendingConflict(),
                snapshot.environment(), snapshot.installedToolVersions()));
        PreviewBasis basis = snapshot.basisFor(result);
        String recommendationId = store.saveIfUnchanged(snapshot, basis, result);
        if (blank(recommendationId)) throw new IllegalStateException("recommendation was not saved");
        return new Created(recommendationId, basis, result);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
