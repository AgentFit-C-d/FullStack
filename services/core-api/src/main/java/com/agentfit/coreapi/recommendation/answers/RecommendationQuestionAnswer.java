package com.agentfit.coreapi.recommendation.answers;

import java.time.Instant;
import java.util.UUID;

public record RecommendationQuestionAnswer(UUID noticeId, UUID recommendationId,
                                           String answerTarget, String answer,
                                           UUID actorId, Instant createdAt, Instant updatedAt) {
    public static String validatedAnswer(String answer) {
        if (answer == null) throw new IllegalArgumentException("answer is required");
        String value = answer.trim();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 4000) {
            throw new IllegalArgumentException("answer must contain 1 to 4000 characters");
        }
        return value;
    }
}
