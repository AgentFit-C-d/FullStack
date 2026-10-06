package com.agentfit.coreapi.recommendation.selection;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.RecommendationDecision.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A-owned, owner-checked recommendation snapshot passed to B before Preview. */
public record StoredRecommendationState(String recommendationId, PreviewBasis basis,
                                        Status status, List<String> toolKeys) {
    public StoredRecommendationState {
        toolKeys = toolKeys == null ? null : Collections.unmodifiableList(new ArrayList<>(toolKeys));
    }
}
