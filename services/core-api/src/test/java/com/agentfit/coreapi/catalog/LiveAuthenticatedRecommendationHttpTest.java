package com.agentfit.coreapi.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentfit.coreapi.domain.auth.entity.AgentFitOAuth2User;
import com.agentfit.coreapi.domain.auth.entity.UserAccount;
import com.agentfit.coreapi.integration.ab.RecommendationInputsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Full Spring Security filter and real A/B beans, with an OAuth-shaped test principal. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "AGENTFIT_LIVE_AI_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AGENTFIT_TEST_POSTGRES_URL", matches = ".+")
class LiveAuthenticatedRecommendationHttpTest {
    private static final String BASE_URL = System.getenv("AGENTFIT_TEST_POSTGRES_URL");
    private static final String DB_USER = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only");
    private static final String SCHEMA = "b_secure_" + UUID.randomUUID().toString().replace("-", "");
    private static final Path CATALOG = Path.of("..", "..", "demo", "catalog-synthetic").toAbsolutePath().normalize();
    private static final String CATALOG_HASH = "453860c7e6d0be7871813e185ca52259bba774426318ced7451efe2b53aee2d6";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecommendationInputsService inputs;
    @Autowired ObjectMapper mapper;

    @BeforeAll
    static void createSchema() throws Exception {
        try (var connection = DriverManager.getConnection(BASE_URL, DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> BASE_URL + (BASE_URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.session.jdbc.initialize-schema", () -> "never");
        registry.add("agentfit.hmac.current-key-id", () -> System.getenv("AGENTFIT_HMAC_CURRENT_KEY_ID"));
        registry.add("agentfit.hmac.current-secret", () -> System.getenv("AGENTFIT_HMAC_CURRENT_SECRET"));
        registry.add("agentfit.ai-service.analysis-url", () -> System.getenv("AGENTFIT_LIVE_AI_URL") + "/internal/v1/analysis");
        registry.add("agentfit.ai-service.needs-url", () -> System.getenv("AGENTFIT_LIVE_AI_URL") + "/internal/v1/skills/needs");
        registry.add("agentfit.ai-service.rank-url", () -> System.getenv("AGENTFIT_LIVE_AI_URL") + "/internal/v1/skills/rank");
        registry.add("agentfit.catalog.directory", () -> CATALOG.toString());
        registry.add("agentfit.catalog.approved-hash", () -> CATALOG_HASH);
        registry.add("agentfit.analysis.worker-poll-interval-ms", () -> "60000");
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection = DriverManager.getConnection(BASE_URL, DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void ownerCanCreateAndReadRecommendationButOtherUserCannot() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        jdbc.update("INSERT INTO \"USER\" (id, name, github_user_id, github_login) VALUES (?, ?, ?, ?)",
            owner, "demo-owner", 991001L, "demo-owner");
        jdbc.update("INSERT INTO project (id, user_id, name, version, current_step, created_at, updated_at) VALUES (?, ?, ?, 1, 'RECOMMENDATION', now(), now())",
            project, owner, "demo");
        jdbc.update("INSERT INTO project_profile (id, project_id, kind, version, project_name, project_type, domain, field_metadata, created_at, updated_at) VALUES (?, ?, 'DRAFT', 1, ?, ?, ?, '{}'::jsonb, now(), now())",
            profile, project, "demo", "web", "development");
        jdbc.update("INSERT INTO project_profile_item (id, project_profile_id, field_key, ordinal, name, source, evidence, created_at, updated_at) VALUES (?, ?, 'features', 0, ?, 'DOCUMENT', '[]'::jsonb, now(), now())",
            UUID.randomUUID(), profile,
            "문서, repo 코드 탐색, 코드 검토, 테스트 작성 및 실행, 브라우저 검증, API 연동, DB 점검, AI 모델 평가");
        mvc.perform(post("/api/projects/" + project + "/profile/confirm")
                .with(authentication(owner)).with(csrf()).contentType("application/json")
                .content("{\"expectedDraftVersion\":1,\"requestId\":\"secure-http-demo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectVersion").value(2));
        org.junit.jupiter.api.Assertions.assertEquals(1, inputs.saveEnvironment(project.toString(), 0,
            "WINDOWS", "demo-claude-code", "demo-1", true, List.of()));

        var ownerAuth = authentication(owner);
        String path = "/api/projects/" + project + "/recommendations";
        mvc.perform(post(path).with(ownerAuth).contentType("application/json")
                .content("{\"expectedVersions\":{\"project\":2,\"developer\":0,\"environment\":1}}"))
            .andExpect(status().isForbidden());
        var created = mvc.perform(post(path).with(authentication(owner)).with(csrf()).contentType("application/json")
                .content("{\"expectedVersions\":{\"project\":2,\"developer\":0,\"environment\":1}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("RECOMMENDED"))
            .andReturn();
        mvc.perform(get(path + "/latest").with(authentication(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RECOMMENDED"));
        mvc.perform(get(path + "/latest").with(authentication(UUID.randomUUID())))
            .andExpect(status().isUnauthorized());
        UUID stranger = UUID.randomUUID();
        jdbc.update("INSERT INTO \"USER\" (id, name) VALUES (?, ?)", stranger, "stranger");
        mvc.perform(get(path + "/latest").with(authentication(stranger)))
            .andExpect(status().isNotFound());

        var createdJson = mapper.readTree(created.getResponse().getContentAsString());
        String recommendationId = createdJson.get("recommendationId").asText();
        String previewRequest = "{\"basis\":" + createdJson.get("basis") + ","
            + "\"recommendationId\":\"" + recommendationId + "\","
            + "\"target\":{\"osFamily\":\"WINDOWS\",\"clientId\":\"demo-claude-code\",\"clientVersion\":\"demo-1\"},"
            + "\"selectedToolKeys\":[\"demo-all-capabilities-skill\"],"
            + "\"permissionSelections\":[{\"toolKey\":\"demo-all-capabilities-skill\",\"mappingKey\":\"read\",\"policy\":\"ASK_EACH_TIME\"}],"
            + "\"existingState\":\"UNKNOWN\",\"providedFiles\":[],\"generatorVersion\":\"static-v1\"}";
        var preview = mvc.perform(post("/api/projects/" + project + "/config-previews")
                .with(authentication(owner)).with(csrf()).contentType("application/json")
                .content(previewRequest))
            .andExpect(status().isCreated())
            .andReturn();
        var previewJson = mapper.readTree(preview.getResponse().getContentAsString());
        String previewId = previewJson.get("previewId").asText();
        String fingerprint = previewJson.get("fingerprint").asText();
        var approval = mvc.perform(post("/api/projects/" + project + "/config-previews/" + previewId + "/approval")
                .with(authentication(owner)).with(csrf()).contentType("application/json")
                .content("{\"fingerprint\":\"" + fingerprint + "\",\"confirmed\":true}"))
            .andExpect(status().isCreated())
            .andReturn();
        String approvalId = mapper.readTree(approval.getResponse().getContentAsString()).get("approvalId").asText();
        String downloadBody = "{\"previewId\":\"" + previewId + "\",\"fingerprint\":\""
            + fingerprint + "\",\"previewRequest\":" + previewRequest + "}";
        var download = mvc.perform(post("/api/projects/" + project + "/config-approvals/" + approvalId + "/download")
                .with(authentication(owner)).with(csrf()).contentType("application/json")
                .content(downloadBody))
            .andExpect(status().isOk())
            .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals("application/zip", download.getResponse().getContentType());
        org.junit.jupiter.api.Assertions.assertTrue(download.getResponse().getContentAsByteArray().length > 4);
        org.junit.jupiter.api.Assertions.assertEquals(0x50, download.getResponse().getContentAsByteArray()[0]);
        org.junit.jupiter.api.Assertions.assertEquals(0x4b, download.getResponse().getContentAsByteArray()[1]);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(download.getResponse().getContentAsByteArray()))) {
            var entry = zip.getNextEntry();
            org.junit.jupiter.api.Assertions.assertNotNull(entry);
            org.junit.jupiter.api.Assertions.assertEquals("config/demo.txt", entry.getName());
            org.junit.jupiter.api.Assertions.assertTrue(new String(zip.readAllBytes(), StandardCharsets.UTF_8)
                .contains("DEMO ONLY"));
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor authentication(UUID userId) {
        var account = new UserAccount(userId, "test-user", 0L, "test-user");
        var oauthUser = new AgentFitOAuth2User(account, Map.of("id", 0), List.of());
        var token = new OAuth2AuthenticationToken(oauthUser, List.of(), "github");
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(token);
    }
}
