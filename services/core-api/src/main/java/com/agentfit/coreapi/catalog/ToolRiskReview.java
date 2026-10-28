package com.agentfit.coreapi.catalog;

/** Reviewer-supplied risk and external-auth statement; never inferred from a tool name. */
public record ToolRiskReview(Status status, Level level, ExternalAuth externalAuth,
                             String reason, VerificationEvidence evidence) {
    public enum Status { UNASSESSED, REVIEWED }
    public enum Level { LOW, MEDIUM, HIGH }
    public enum ExternalAuth { REQUIRED, NOT_REQUIRED }

    public ToolRiskReview {
        if (status == null) throw new IllegalArgumentException("risk review status required");
        if (status == Status.UNASSESSED) {
            if (level != null || externalAuth != null || reason != null || evidence != null) {
                throw new IllegalArgumentException("unassessed risk cannot contain claims");
            }
        } else if (level == null || externalAuth == null || reason == null || reason.isBlank()
            || reason.length() > 500 || reason.codePoints().anyMatch(Character::isISOControl)
            || evidence == null) {
            throw new IllegalArgumentException("incomplete risk review");
        }
    }

    public static ToolRiskReview unassessed() {
        return new ToolRiskReview(Status.UNASSESSED, null, null, null, null);
    }
}
