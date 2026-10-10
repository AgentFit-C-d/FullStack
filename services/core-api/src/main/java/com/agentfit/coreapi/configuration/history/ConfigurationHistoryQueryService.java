package com.agentfit.coreapi.configuration.history;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import java.util.Objects;

/** B's content-free generation read boundary; A supplies only owner-checked records. */
public final class ConfigurationHistoryQueryService {
    /** A authenticates the caller and loads the project's current version basis. */
    public interface OwnerCheckedBasisReader {
        PreviewBasis load(String projectId);
    }

    /** A selects the generation within the authenticated project. */
    public interface GenerationReader {
        ConfigurationGenerationHistory find(String projectId, String generationId);
    }

    /** A selects the latest USER report for that generation, if any. */
    public interface LatestReportReader {
        ConfigurationHistoryReadModel.UserReport latest(String projectId, String generationId);
    }

    private final OwnerCheckedBasisReader bases;
    private final GenerationReader generations;
    private final LatestReportReader reports;

    public ConfigurationHistoryQueryService(OwnerCheckedBasisReader bases,
                                            GenerationReader generations,
                                            LatestReportReader reports) {
        this.bases = Objects.requireNonNull(bases);
        this.generations = Objects.requireNonNull(generations);
        this.reports = Objects.requireNonNull(reports);
    }

    public ConfigurationHistoryReadModel find(String projectId, String generationId) {
        if (blank(projectId) || blank(generationId)) {
            throw new IllegalArgumentException("project and generation required");
        }
        PreviewBasis current = Objects.requireNonNull(bases.load(projectId));
        if (!projectId.equals(current.projectId())) {
            throw new IllegalArgumentException("current basis belongs to another project");
        }
        ConfigurationGenerationHistory generation = generations.find(projectId, generationId);
        if (generation == null) return null;
        if (!generationId.equals(generation.id()) || generation.basis() == null
            || !projectId.equals(generation.basis().projectId())) {
            throw new IllegalArgumentException("generation belongs to another project");
        }
        return ConfigurationHistoryReadModel.project(generation, current,
            reports.latest(projectId, generationId));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
