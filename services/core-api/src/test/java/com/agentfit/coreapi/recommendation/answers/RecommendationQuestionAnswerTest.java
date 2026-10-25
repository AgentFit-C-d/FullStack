package com.agentfit.coreapi.recommendation.answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.global.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Test
    void rejectsAnswerWhenLatestRecommendationIsStale() {
        var id = UUID.randomUUID();
        var project = UUID.randomUUID();
        var summary = new RecommendationReadModel(id.toString(),
            RecommendationDecision.Status.NEEDS_INFORMATION,
            RecommendationReadModel.Validity.STALE, null, List.of());
        var latest = new RecommendationLatestDetailsService(
            ignored -> null, ignored -> null, () -> null) {
            @Override public Snapshot latest(String ignored) {
                return new Snapshot(summary, null, null,
                    List.of(), List.of(), List.of(), List.of());
            }
        };

        var service = new RecommendationQuestionAnswerService(new JdbcTemplate(), latest);
        assertThrows(BusinessException.class, () -> service.save(project.toString(),
            id.toString(), UUID.randomUUID().toString(), "확인했습니다", UUID.randomUUID()));
    }
}
