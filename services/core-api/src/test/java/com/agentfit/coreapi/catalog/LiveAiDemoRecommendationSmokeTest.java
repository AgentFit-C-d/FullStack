package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.agentfit.coreapi.domain.analysis.client.HttpAiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.config.AiAnalysisClientConfiguration;
import com.agentfit.coreapi.domain.analysis.service.HmacKeyRing;
import com.agentfit.coreapi.integration.ab.AiCapabilityAnalyzerAdapter;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Opt-in smoke against the running FastAPI service; needs does not contact a model provider. */
class LiveAiDemoRecommendationSmokeTest {
    @Test
    void confirmedFeatureBecomesRecommendationThroughLiveHmacNeeds() {
        String baseUrl = System.getenv("AGENTFIT_LIVE_AI_URL");
        assumeTrue(baseUrl != null && !baseUrl.isBlank(), "live AI smoke is opt-in");
        String keyId = System.getenv("AGENTFIT_HMAC_CURRENT_KEY_ID");
        String secret = System.getenv("AGENTFIT_HMAC_CURRENT_SECRET");
        assumeTrue(keyId != null && !keyId.isBlank() && secret != null && !secret.isBlank());

        var ring = HmacKeyRing.from(new HmacKeyRing.Configuration(keyId, secret,
            null, null, null, Instant.now()));
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("live-ai-demo", Map.of(
            "agentfit.ai-service.analysis-url", baseUrl + "/internal/v1/analysis",
            "agentfit.ai-service.needs-url", baseUrl + "/internal/v1/skills/needs",
            "agentfit.ai-service.rank-url", baseUrl + "/internal/v1/skills/rank")));
        context.getBeanFactory().registerSingleton("demoObjectMapper", new ObjectMapper());
        context.getBeanFactory().registerSingleton("demoHmacKeyRing", ring);
        context.register(AiAnalysisClientConfiguration.class);
        context.refresh();
        HttpAiAnalysisClient client = context.getBean(HttpAiAnalysisClient.class);
        var catalog = Path.of("..", "..", "demo", "catalog-synthetic").toAbsolutePath().normalize();
        String hash = "30c2557218e9d92c7b2d254c8df3aaf5d8960ebb13aece7f7146d1f65b567c7f";
        String field = "profile.item.features.0";
        var snapshot = new RecommendationCreationService.OwnerCheckedSnapshot(
            "demo-project", 1, "confirmed-profile", 1, "confirmation-event", 0, 0, 0,
            Set.of(field), Map.of(field,
                "문서, repo 코드 탐색, 코드 검토, 테스트 작성 및 실행, 브라우저 검증, API 연동, DB 점검, AI 모델 평가"),
            false, new EnvironmentTarget("WINDOWS", "demo-claude-code", "demo-1"), Map.of());
        var service = new RecommendationCreationService(
            project -> snapshot,
            new AiCapabilityAnalyzerAdapter(client, new ObjectMapper()),
            () -> new RecommendationCreationService.ApprovedCatalog(catalog, hash),
            (current, basis, result) -> "live-ai-demo-recommendation");

        var created = service.create("demo-project", new RecommendationCreationService.ExpectedVersions(1, 0, 0));
        assertEquals(RecommendationDecision.Status.RECOMMENDED, created.result().decision().status());
        assertEquals("demo-all-capabilities-skill", created.result().decision().toolKeys().getFirst());
        assertTrue(created.result().clarificationQuestions().isEmpty());
        context.close();
    }
}
