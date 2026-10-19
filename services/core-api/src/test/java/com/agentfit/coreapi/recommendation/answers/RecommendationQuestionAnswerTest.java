package com.agentfit.coreapi.recommendation.answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RecommendationQuestionAnswerTest {
    @Test
    void trimsAndLimitsSavedAnswer() {
        assertEquals("확인했습니다", RecommendationQuestionAnswer.validatedAnswer("  확인했습니다  "));
        assertThrows(IllegalArgumentException.class,
            () -> RecommendationQuestionAnswer.validatedAnswer("  "));
        assertThrows(IllegalArgumentException.class,
            () -> RecommendationQuestionAnswer.validatedAnswer("가".repeat(4001)));
    }
}
