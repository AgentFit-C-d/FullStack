package com.agentfit.coreapi.recommendation;

import java.util.List;

public record RecommendationDecision(Status status, List<String> toolKeys, List<String> questionCodes) {
    public enum Status {
        RECOMMENDED,
        NO_ADDITIONS_NEEDED,
        NEEDS_INFORMATION,
        NO_COMPATIBLE_TOOLS
    }

    public RecommendationDecision {
        toolKeys = List.copyOf(toolKeys);
        questionCodes = List.copyOf(questionCodes);
    }
}
