package com.agentfit.coreapi.recommendation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.catalog.ToolRiskReview;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

class JdbcRiskReviewRowTest {
    @Test
    void restoresReviewedAndUnassessedRowsAndRejectsContradictoryData() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:risk-row;DB_CLOSE_DELAY=-1")) {
            var sql = "SELECT 1 AS a, 2 AS b, 3 AS c, 4 AS d, 5 AS e, 6 AS f, 7 AS g, "
                + "'REVIEWED' AS risk_status, 'HIGH' AS risk_level, 'REQUIRED' AS external_auth, "
                + "'External authorization required' AS risk_reason, "
                + "'https://example.org/review' AS risk_evidence_url, "
                + "DATE '2026-10-26' AS risk_checked_at";
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
                rows.next();
                var risk = JdbcLatestRecommendationReader.riskReview(rows);
                assertEquals(ToolRiskReview.Level.HIGH, risk.level());
                assertEquals(ToolRiskReview.ExternalAuth.REQUIRED, risk.externalAuth());
                assertEquals("https://example.org/review", risk.evidence().sourceUrl());
            }
            var unassessed = "SELECT 1, 2, 3, 4, 5, 6, 7, 'UNASSESSED', NULL, NULL, NULL, NULL, NULL";
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(unassessed)) {
                rows.next();
                assertEquals(ToolRiskReview.Status.UNASSESSED,
                    JdbcLatestRecommendationReader.riskReview(rows).status());
            }
            var contradictory = "SELECT 1, 2, 3, 4, 5, 6, 7, 'UNASSESSED', 'LOW', NULL, NULL, NULL, NULL";
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(contradictory)) {
                rows.next();
                assertThrows(IllegalStateException.class,
                    () -> JdbcLatestRecommendationReader.riskReview(rows));
            }
            var incomplete = "SELECT 1, 2, 3, 4, 5, 6, 7, 'REVIEWED', NULL, 'REQUIRED', "
                + "'External authorization required', 'https://example.org/review', DATE '2026-10-26'";
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(incomplete)) {
                rows.next();
                assertThrows(IllegalStateException.class,
                    () -> JdbcLatestRecommendationReader.riskReview(rows));
            }
        }
    }
}
