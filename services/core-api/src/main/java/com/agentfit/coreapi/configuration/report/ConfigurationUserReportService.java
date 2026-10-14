package com.agentfit.coreapi.configuration.report;

import com.agentfit.coreapi.configuration.history.ConfigurationHistoryReadModel;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Records a user's application statement after A's owner-checked generation lookup. */
public final class ConfigurationUserReportService {
    /** A authenticates the caller and checks ownership of a persisted generation. */
    public interface TrustedGenerationReader {
        TrustedGeneration load(String projectId, String generationId);
    }

    /** A saves only the user statement and server metadata, with an ownership recheck. */
    public interface ReportStore {
        ConfigurationHistoryReadModel.UserReport saveIfCurrent(
            TrustedGeneration generation, ConfigurationUserReport.Accepted accepted, String reportId);
    }

    public record TrustedGeneration(String projectId, String generationId, Instant createdAt) {}

    private final TrustedGenerationReader generations;
    private final Supplier<String> serverReportIds;
    private final Clock serverClock;
    private final ReportStore store;

    public ConfigurationUserReportService(TrustedGenerationReader generations,
                                          Supplier<String> serverReportIds, Clock serverClock,
                                          ReportStore store) {
        this.generations = Objects.requireNonNull(generations);
        this.serverReportIds = Objects.requireNonNull(serverReportIds);
        this.serverClock = Objects.requireNonNull(serverClock);
        this.store = Objects.requireNonNull(store);
    }

    public ConfigurationHistoryReadModel.UserReport report(
        String projectId, String generationId, ConfigurationUserReport.Submission submission) {
        if (blank(projectId) || blank(generationId)) {
            throw new IllegalArgumentException("project and generation required");
        }
        TrustedGeneration generation = Objects.requireNonNull(
            generations.load(projectId, generationId));
        if (!projectId.equals(generation.projectId())
            || !generationId.equals(generation.generationId())
            || generation.createdAt() == null) {
            throw new IllegalArgumentException("generation belongs to another project or is incomplete");
        }
        ConfigurationUserReport.Accepted accepted = ConfigurationUserReport.accept(submission, serverClock);
        if (accepted.reportedAt().isBefore(generation.createdAt())) {
            throw new IllegalArgumentException("report predates generation");
        }
        String reportId = serverReportIds.get();
        if (blank(reportId) || reportId.codePointCount(0, reportId.length()) > 128) {
            throw new IllegalArgumentException("invalid server report ID");
        }
        ConfigurationHistoryReadModel.UserReport saved = store.saveIfCurrent(generation, accepted, reportId);
        if (saved == null || !Objects.equals(reportId, saved.id())
            || !generationId.equals(saved.configurationId())
            || saved.reportedState() != accepted.reportedState()
            || saved.reasonCode() != accepted.reasonCode()
            || saved.source() != ConfigurationUserReport.Source.USER
            || !accepted.reportedAt().equals(saved.reportedAt())) {
            throw new IllegalStateException("user report was not saved correctly");
        }
        return saved;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
