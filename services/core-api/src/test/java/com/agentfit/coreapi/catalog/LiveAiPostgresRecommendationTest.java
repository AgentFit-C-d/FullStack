package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.domain.analysis.client.HttpAiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.config.AiAnalysisClientConfiguration;
import com.agentfit.coreapi.domain.analysis.service.HmacKeyRing;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.domain.project.service.ProjectOwnershipGuard;
import com.agentfit.coreapi.global.exception.GlobalExceptionHandler;
import com.agentfit.coreapi.integration.ab.AiCapabilityAnalyzerAdapter;
import com.agentfit.coreapi.integration.ab.JdbcRecommendationStoreAdapter;
import com.agentfit.coreapi.integration.ab.JdbcPreviewContextAdapter;
import com.agentfit.coreapi.integration.ab.JdbcPreviewStoreAdapter;
import com.agentfit.coreapi.integration.ab.OwnerCheckedSnapshotReaderAdapter;
import com.agentfit.coreapi.integration.ab.RecommendationInputsService;
import com.agentfit.coreapi.recommendation.controller.RecommendationController;
import com.agentfit.coreapi.recommendation.persistence.JdbcLatestRecommendationPorts;
import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in full A confirmed profile -> live AI needs -> B PostgreSQL save check. */
class LiveAiPostgresRecommendationTest {
    @Test
    void confirmedProfileSupportsRecommendationAndPreviewHttpThroughLiveAi() throws Exception {
        String baseUrl = System.getenv("AGENTFIT_LIVE_AI_URL");
        String jdbcUrl = System.getenv("AGENTFIT_TEST_POSTGRES_URL");
        String keyId = System.getenv("AGENTFIT_HMAC_CURRENT_KEY_ID");
        String secret = System.getenv("AGENTFIT_HMAC_CURRENT_SECRET");
        boolean realReviewCatalog = "REAL_REVIEW".equals(System.getenv("AGENTFIT_LIVE_CATALOG_KIND"));
        String clientId = realReviewCatalog ? "claude-code" : "demo-claude-code";
        String clientVersion = realReviewCatalog
            ? System.getenv().getOrDefault("AGENTFIT_LIVE_CATALOG_CLIENT_VERSION", "2.1.270") : "demo-1";
        String toolKey = realReviewCatalog ? "obra-superpowers-test-driven-development"
            : "demo-all-capabilities-skill";
        assumeTrue(baseUrl != null && !baseUrl.isBlank() && jdbcUrl != null && !jdbcUrl.isBlank()
            && keyId != null && !keyId.isBlank() && secret != null && !secret.isBlank(),
            "live AI and PostgreSQL smoke is opt-in");
        String user = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit");
        String password = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only");
        String schema = "b_live_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(jdbcUrl, user, password).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").load().migrate();
        try {
            var dataSource = new DriverManagerDataSource(jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?")
                + "currentSchema=" + schema, user, password);
            var jdbc = new JdbcTemplate(dataSource);
            UUID owner = UUID.randomUUID();
            UUID project = UUID.randomUUID();
            UUID profile = UUID.randomUUID();
            UUID confirmation = UUID.randomUUID();
            jdbc.update("INSERT INTO \"USER\" (id, name) VALUES (?, ?)", owner, "demo-owner");
            jdbc.update("INSERT INTO project (id, user_id, name, version, current_step, created_at, updated_at) VALUES (?, ?, ?, 1, 'RECOMMENDATION', now(), now())",
                project, owner, "demo");
            jdbc.update("INSERT INTO project_profile (id, project_id, kind, version, project_name, project_type, domain, field_metadata, confirmed_by, confirmed_at, created_at, updated_at) VALUES (?, ?, 'CONFIRMED', 1, ?, ?, ?, '{}'::jsonb, ?, now(), now(), now())",
                profile, project, "demo", "web", "development", owner);
            jdbc.update("INSERT INTO project_profile_item (id, project_profile_id, field_key, ordinal, name, source, evidence, created_at, updated_at) VALUES (?, ?, 'features', 0, ?, 'DOCUMENT', '[]'::jsonb, now(), now())",
                UUID.randomUUID(), profile,
                realReviewCatalog ? "Write tests" : "브라우저 검증");
            jdbc.update("INSERT INTO profile_confirmation_event (id, project_id, confirmed_profile_id, confirmed_profile_version, project_version, actor_id, confirmed_at, request_id, profile_snapshot) VALUES (?, ?, ?, 1, 1, ?, now(), ?, '{}'::jsonb)",
                confirmation, project, profile, owner, "live-ai-db-demo");
            assertEquals(1, new RecommendationInputsService(dataSource).saveEnvironment(project.toString(), 0,
                "WINDOWS", clientId, clientVersion, true, java.util.List.of()));

            var ring = HmacKeyRing.from(new HmacKeyRing.Configuration(keyId, secret,
                null, null, null, Instant.now()));
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("live-ai-demo", Map.of(
                    "agentfit.ai-service.analysis-url", baseUrl + "/internal/v1/analysis",
                    "agentfit.ai-service.needs-url", baseUrl + "/internal/v1/skills/needs",
                    "agentfit.ai-service.rank-url", baseUrl + "/internal/v1/skills/rank")));
                context.getBeanFactory().registerSingleton("demoObjectMapper", new ObjectMapper());
                context.getBeanFactory().registerSingleton("demoHmacKeyRing", ring);
                context.register(AiAnalysisClientConfiguration.class);
                context.refresh();
                var catalog = realReviewCatalog
                    ? Path.of(System.getenv().getOrDefault("AGENTFIT_LIVE_CATALOG_DIRECTORY",
                        Path.of("..", "..", "catalog-candidates", "obra-tdd-windows-claude-2.1.270-verified")
                            .toAbsolutePath().normalize().toString()))
                    : Path.of("..", "..", "demo", "catalog-synthetic").toAbsolutePath().normalize();
                var snapshots = new OwnerCheckedSnapshotReaderAdapter(jdbc);
                var approved = new ServerConfiguredCatalogReader(catalog,
                    realReviewCatalog
                        ? System.getenv().getOrDefault("AGENTFIT_LIVE_CATALOG_HASH",
                            "9baf88eda910d9fc96a35163aeacb9ae0c08edd04a3bef9f5ed46c47da3ddf90")
                        : "30c2557218e9d92c7b2d254c8df3aaf5d8960ebb13aece7f7146d1f65b567c7f");
                var service = new RecommendationCreationService(
                    snapshots,
                    new AiCapabilityAnalyzerAdapter(context.getBean(HttpAiAnalysisClient.class), new ObjectMapper()),
                    approved,
                    new JdbcRecommendationStoreAdapter(dataSource));
                var beans = new DefaultListableBeanFactory();
                beans.registerSingleton("approvedCatalog", approved);
                var ports = new JdbcLatestRecommendationPorts(snapshots,
                    beans.getBeanProvider(RecommendationCreationService.ApprovedCatalogReader.class), dataSource);
                beans.registerSingleton("recommendationWorkflow", service);
                var readTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                beans.registerSingleton("latestRecommendationWorkflow",
                    new RecommendationLatestDetailsService(ports, ports, approved) {
                        @Override
                        public Snapshot latest(String projectId) {
                            return readTransaction.execute(status -> super.latest(projectId));
                        }
                    });
                beans.registerSingleton("previewWorkflow", new PreviewCreationService(
                    new JdbcPreviewContextAdapter(dataSource), approved,
                    new JdbcPreviewStoreAdapter(dataSource)));
                var principal = new AuthenticatedPrincipal(0, "demo-owner", null, owner);
                var principals = new SessionPrincipalResolver(null) {
                    @Override
                    public Optional<AuthenticatedPrincipal> resolve(jakarta.servlet.http.HttpServletRequest request) {
                        return Optional.of(principal);
                    }
                };
                var ownerChecks = new AtomicInteger();
                var ownership = new ProjectOwnershipGuard(null) {
                    @Override
                    public com.agentfit.coreapi.domain.project.entity.Project requireOwned(
                            AuthenticatedPrincipal authenticated, String projectId) {
                        assertEquals(principal, authenticated);
                        assertEquals(project.toString(), projectId);
                        ownerChecks.incrementAndGet();
                        return null;
                    }
                };
                var mvc = MockMvcBuilders.standaloneSetup(new RecommendationController(principals, ownership,
                    beans.getBeanProvider(RecommendationCreationService.class),
                    beans.getBeanProvider(RecommendationLatestDetailsService.class),
                    beans.getBeanProvider(PreviewCreationService.class)))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
                String path = "/api/projects/" + project + "/recommendations";
                var created = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersions\":{\"project\":1,\"developer\":0,\"environment\":1}}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("RECOMMENDED"))
                    .andReturn();
                var createdJson = new ObjectMapper().readTree(created.getResponse().getContentAsString());
                String recommendationId = createdJson.get("recommendationId").asText();
                UUID recommendation = UUID.fromString(recommendationId);
                assertNotNull(jdbc.queryForObject("SELECT id FROM recommendation WHERE id = ?",
                    UUID.class, recommendation));
                assertEquals(9, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_capability WHERE recommendation_id = ?",
                    Integer.class, recommendation));
                assertEquals(realReviewCatalog ? 7 : 8, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_capability WHERE recommendation_id = ? AND necessity = 'NOT_REQUESTED'",
                    Integer.class, recommendation));
                assertEquals(realReviewCatalog ? 2 : 1, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_capability WHERE recommendation_id = ? AND necessity = 'REQUIRED'",
                    Integer.class, recommendation));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_item WHERE recommendation_id = ?",
                    Integer.class, recommendation));
                if (realReviewCatalog) {
                    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_notice WHERE recommendation_id = ? AND code = 'partial_required_coverage'",
                        Integer.class, recommendation));
                }
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND event = 'RECOMMENDATION_CREATED'",
                    Integer.class, recommendation));
                mvc.perform(get(path + "/latest"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recommendationId").value(recommendationId))
                    .andExpect(jsonPath("$.status").value("RECOMMENDED"))
                    .andExpect(jsonPath("$.validity").value("CURRENT"));
                String previewBody = "{\"basis\":" + createdJson.get("basis") + ","
                    + "\"recommendationId\":\"" + recommendationId + "\","
                    + "\"target\":{\"osFamily\":\"WINDOWS\",\"clientId\":\"" + clientId
                    + "\",\"clientVersion\":\"" + clientVersion + "\"},"
                    + "\"selectedToolKeys\":[\"" + toolKey + "\"],"
                    + (realReviewCatalog
                        ? "\"permissionSelections\":[{\"toolKey\":\"" + toolKey + "\",\"mappingKey\":\"edit-project-files\",\"policy\":\"ASK_EACH_TIME\"},{\"toolKey\":\"" + toolKey + "\",\"mappingKey\":\"run-project-tests\",\"policy\":\"ASK_EACH_TIME\"}],"
                        : "\"permissionSelections\":[{\"toolKey\":\"" + toolKey + "\",\"mappingKey\":\"read\",\"policy\":\"ASK_EACH_TIME\"}],")
                    + "\"existingState\":\"UNKNOWN\",\"providedFiles\":[],\"generatorVersion\":\"static-v1\"}";
                var preview = mvc.perform(post("/api/projects/" + project + "/config-previews")
                        .contentType(MediaType.APPLICATION_JSON).content(previewBody))
                    .andReturn();
                assertEquals(201, preview.getResponse().getStatus(),
                    preview.getResponse().getContentAsString());
                assertEquals(recommendationId, new ObjectMapper().readTree(
                    preview.getResponse().getContentAsString()).get("recommendationId").asText());
                if (realReviewCatalog) {
                    assertEquals("cap_test_execution", new ObjectMapper().readTree(
                        preview.getResponse().getContentAsString()).get("unmetCapabilityKeys").get(0).asText());
                }
                String previewId = new ObjectMapper().readTree(preview.getResponse().getContentAsString())
                    .get("previewId").asText();
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM config_preview WHERE id = ?",
                    Integer.class, UUID.fromString(previewId)));
                assertEquals(3, ownerChecks.get());
            }
        } finally {
            try (var connection = DriverManager.getConnection(jdbcUrl, user, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
