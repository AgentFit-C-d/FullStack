package com.agentfit.coreapi.configuration.history;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import java.time.Instant;

/** Content-free projection of an A-owned generation and optional latest user statement. */
public final class ConfigurationHistoryReadModel {
    private final ConfigurationGenerationHistory generation;
    private final ConfigurationGenerationHistory.Validity validity;
    private final UserReport latestUserReport;

    private ConfigurationHistoryReadModel(ConfigurationGenerationHistory generation,
                                          ConfigurationGenerationHistory.Validity validity,
                                          UserReport latestUserReport) {
        this.generation = generation;
        this.validity = validity;
        this.latestUserReport = latestUserReport;
    }

    public ConfigurationGenerationHistory generation() { return generation; }
    public ConfigurationGenerationHistory.Validity validity() { return validity; }
    public UserReport latestUserReport() { return latestUserReport; }

    public record UserReport(String id, String configurationId,
                             ConfigurationUserReport.ReportedState reportedState,
                             ConfigurationUserReport.ReasonCode reasonCode,
                             ConfigurationUserReport.Source source, Instant reportedAt) {
        public UserReport {
            if (blank(id) || blank(configurationId) || reportedState == null
                || source != ConfigurationUserReport.Source.USER || reportedAt == null
                || reportedState == ConfigurationUserReport.ReportedState.APPLIED && reasonCode != null
                || reportedState == ConfigurationUserReport.ReportedState.FAILED && reasonCode == null) {
                throw new IllegalArgumentException("invalid stored user report");
            }
        }
    }

    public static ConfigurationHistoryReadModel project(ConfigurationGenerationHistory generation,
                                                         PreviewBasis currentBasis,
                                                         UserReport latestUserReport) {
        if (generation == null || currentBasis == null
            || !generation.basis().projectId().equals(currentBasis.projectId())
            || latestUserReport != null && (!generation.id().equals(latestUserReport.configurationId())
                || latestUserReport.reportedAt().isBefore(generation.createdAt()))) {
            throw new IllegalArgumentException("invalid generation history read input");
        }
        return new ConfigurationHistoryReadModel(generation, generation.validity(currentBasis),
            latestUserReport);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
