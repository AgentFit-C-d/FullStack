package com.agentfit.coreapi.recommendation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationLatestResponseTest {
    @Test
    void staleResponseRetainsSavedReasonWithoutExposingCurrentPermissions() {
        var storedBasis = new PreviewBasis("project-1", 1, "profile-1", 1, "event-1",
            0, 0, 1, "release-1", "a".repeat(64));
        var currentBasis = new PreviewBasis("project-1", 2, "profile-1", 1, "event-1",
            0, 0, 1, "release-1", "a".repeat(64));
        var summary = RecommendationReadModel.latest(new StoredRecommendationState(
            "recommendation-1", storedBasis, RecommendationDecision.Status.RECOMMENDED,
            List.of("tool-1")), currentBasis);
        var saved = new JdbcRecommendationMetadataWriter.Item("item-1", "tool-1", "1.0",
            "support-1", true, "저장된 추천 이유", JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE);
        var snapshot = new RecommendationLatestDetailsService.Snapshot(summary,
            JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.parse("2026-10-25T00:00:00Z"), List.of(), List.of(saved), List.of(), List.of());

        var response = RecommendationController.latestResponse(snapshot);
        var json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).valueToTree(response);

        assertEquals("STALE", json.get("validity").asText());
        assertEquals("ENVIRONMENT_DECLARED", json.get("evidenceLevel").asText());
        assertEquals("2026-10-25T00:00:00Z", json.get("createdAt").asText());
        assertEquals(0, json.get("items").size());
        assertEquals("tool-1", json.get("savedItems").get(0).get("toolKey").asText());
        assertEquals("저장된 추천 이유", json.get("savedItems").get(0).get("reason").asText());
        assertFalse(json.get("savedItems").get(0).has("permissionOptions"));
    }
}
