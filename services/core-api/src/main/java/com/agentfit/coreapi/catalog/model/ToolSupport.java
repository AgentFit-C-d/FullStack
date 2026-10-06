package com.agentfit.coreapi.catalog.model;

import com.agentfit.coreapi.recommendation.EnvironmentTarget;
/** A single, exactly matched Codex product/OS/version verification record. */
public record ToolSupport(
    String key,
    String osFamily,
    String clientId,
    String clientVersion,
    Check documentation,
    Check format,
    Check standalone
) {
    public enum Check { PASS, FAIL, NOT_RUN }

    public boolean verifiedFor(EnvironmentTarget target) {
        return osFamily.equals(target.osFamily())
            && clientId.equals(target.clientId())
            && clientVersion.equals(target.clientVersion())
            && documentation == Check.PASS
            && format == Check.PASS
            && standalone == Check.PASS;
    }
}
