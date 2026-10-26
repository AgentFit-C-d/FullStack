package com.agentfit.coreapi.recommendation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.catalog.CatalogRecommendationDetails;
import com.agentfit.coreapi.catalog.CatalogRecommendationWorkflow;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.catalog.VerificationEvidence;
import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationLatestResponseTest {
    @Test
    void createdResponseAlsoKeepsOptionalItemsSeparate() {
        var basis = new PreviewBasis("project-1", 1, "profile-1", 1, "event-1",
            0, 0, 1, "release-1", "a".repeat(64));
        var evidence = new VerificationEvidence("https://example.org/review",
            LocalDate.of(2026, 10, 26));
        var base = new CatalogRecommendationDetails.Item("base", "Base",
            CatalogTool.Kind.SKILL, "https://example.org/base", "1.0", "support-base",
            Set.of("cap_document_reference"), Set.of(), true, evidence, List.of(), "필수 도구");
        var optional = new CatalogRecommendationDetails.Item("browser", "Browser",
            CatalogTool.Kind.SKILL, "https://example.org/browser", "1.0", "support-browser",
            Set.of(), Set.of("cap_browser_verification"), false, evidence, List.of(), "선택 도구");
        var result = new CatalogRecommendationWorkflow.Result("release-1", "a".repeat(64),
            new RecommendationDecision(RecommendationDecision.Status.RECOMMENDED,
                List.of("base", "browser"), List.of(), List.of()),
            List.of(), List.of(), List.of(base, optional));

        var response = RecommendationController.recommendationResponse(
            new RecommendationCreationService.Created("recommendation-1", basis, result));

        assertEquals(List.of("base"), response.items().stream().map(
            CatalogRecommendationDetails.Item::toolKey).toList());
        assertEquals(List.of("browser"), response.optionalItems().stream().map(
            CatalogRecommendationDetails.Item::toolKey).toList());
    }

    @Test
    void currentResponseKeepsOptionalItemsOutOfDefaultSelectionArray() {
        var basis = new PreviewBasis("project-1", 1, "profile-1", 1, "event-1",
            0, 0, 1, "release-1", "a".repeat(64));
        var summary = RecommendationReadModel.latest(new StoredRecommendationState(
            "recommendation-1", basis, RecommendationDecision.Status.RECOMMENDED,
            List.of("base", "browser")), basis);
        var evidence = new VerificationEvidence("https://example.org/review",
            LocalDate.of(2026, 10, 26));
        var base = new CatalogRecommendationDetails.Item("base", "Base",
            CatalogTool.Kind.SKILL, "https://example.org/base", "1.0", "support-base",
            Set.of("cap_document_reference"), Set.of(), true, evidence, List.of(), "필수 도구");
        var optional = new CatalogRecommendationDetails.Item("browser", "Browser",
            CatalogTool.Kind.SKILL, "https://example.org/browser", "1.0", "support-browser",
            Set.of(), Set.of("cap_browser_verification"), false, evidence, List.of(), "선택 도구");
        var snapshot = new RecommendationLatestDetailsService.Snapshot(summary,
            JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.parse("2026-10-26T00:00:00Z"), List.of(), List.of(), List.of(),
            List.of(base, optional));

        var json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .valueToTree(RecommendationController.latestResponse(snapshot));

        assertEquals(1, json.get("items").size());
        assertEquals("base", json.get("items").get(0).get("toolKey").asText());
        assertEquals("browser", json.get("optionalItems").get(0).get("toolKey").asText());
    }

    @Test
    void staleResponseRetainsSavedReasonWithoutExposingCurrentPermissions() {
        var storedBasis = new PreviewBasis("project-1", 1, "profile-1", 1, "event-1",
            0, 0, 1, "release-1", "a".repeat(64));
        var currentBasis = new PreviewBasis("project-1", 2, "profile-1", 1, "event-1",
            0, 0, 1, "release-1", "a".repeat(64));
        var summary = RecommendationReadModel.latest(new StoredRecommendationState(
            "recommendation-1", storedBasis, RecommendationDecision.Status.RECOMMENDED,
            List.of("tool-1", "tool-2")), currentBasis);
        var saved = new JdbcRecommendationMetadataWriter.Item("item-1", "tool-1", "1.0",
            "support-1", true, "저장된 추천 이유", JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE);
        var optional = new JdbcRecommendationMetadataWriter.Item("item-2", "tool-2", "1.0",
            "support-2", false, "선택 도구 이유", JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE);
        var snapshot = new RecommendationLatestDetailsService.Snapshot(summary,
            JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
            Instant.parse("2026-10-25T00:00:00Z"), List.of(), List.of(saved, optional), List.of(), List.of());

        var response = RecommendationController.latestResponse(snapshot);
        var json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).valueToTree(response);

        assertEquals("STALE", json.get("validity").asText());
        assertEquals("ENVIRONMENT_DECLARED", json.get("evidenceLevel").asText());
        assertEquals("2026-10-25T00:00:00Z", json.get("createdAt").asText());
        assertEquals(0, json.get("items").size());
        assertEquals("tool-1", json.get("savedItems").get(0).get("toolKey").asText());
        assertEquals("저장된 추천 이유", json.get("savedItems").get(0).get("reason").asText());
        assertEquals(false, json.get("savedItems").get(1).get("required").asBoolean());
        assertFalse(json.get("savedItems").get(0).has("permissionOptions"));
    }
}
