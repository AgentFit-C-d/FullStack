package com.agentfit.coreapi.configuration.report;

import java.time.Clock;
import java.time.Instant;

/** A user's statement about applying a downloaded configuration, never a verification result. */
public final class ConfigurationUserReport {
    private ConfigurationUserReport() {}

    public enum ReportedState { APPLIED, FAILED }

    public enum ReasonCode { COPY_FAILED, AUTH_REQUIRED, DEPENDENCY_MISSING, UNKNOWN }

    public enum Source { USER }

    /** Only these two fields may be taken from the user; HTTP parsing must reject extras. */
    public record Submission(ReportedState reportedState, ReasonCode reasonCode) {}

    public static final class Accepted {
        private final ReportedState reportedState;
        private final ReasonCode reasonCode;
        private final Instant reportedAt;

        private Accepted(ReportedState reportedState, ReasonCode reasonCode, Instant reportedAt) {
            this.reportedState = reportedState;
            this.reasonCode = reasonCode;
            this.reportedAt = reportedAt;
        }

        public ReportedState reportedState() { return reportedState; }
        public ReasonCode reasonCode() { return reasonCode; }
        public Source source() { return Source.USER; }
        public Instant reportedAt() { return reportedAt; }
    }

    public static Accepted accept(Submission submitted, Clock serverClock) {
        if (submitted == null || submitted.reportedState() == null || serverClock == null
            || (submitted.reportedState() == ReportedState.APPLIED && submitted.reasonCode() != null)
            || (submitted.reportedState() == ReportedState.FAILED && submitted.reasonCode() == null)) {
            throw new IllegalArgumentException("invalid configuration user report");
        }
        return new Accepted(submitted.reportedState(), submitted.reasonCode(), Instant.now(serverClock));
    }
}
