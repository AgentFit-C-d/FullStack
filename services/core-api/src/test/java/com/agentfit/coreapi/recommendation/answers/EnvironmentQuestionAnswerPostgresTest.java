package com.agentfit.coreapi.recommendation.answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentfit.coreapi.catalog.RecommendationLatestDetailsService;
import com.agentfit.coreapi.integration.ab.RecommendationInputsService;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "AGENTFIT_TEST_POSTGRES_URL", matches = ".+")
class EnvironmentQuestionAnswerPostgresTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID project = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    private final UUID confirmation = UUID.randomUUID();
    private final UUID recommendation = UUID.randomUUID();
    private final UUID notice = UUID.randomUUID();
    private String schema;
    private String url;
    private String user;
    private String password;

    @BeforeEach void migrate() {
        url = System.getenv("AGENTFIT_TEST_POSTGRES_URL");
        user = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit");
        password = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only");
        schema = "b_answer_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").load().migrate();
    }

    @AfterEach void cleanup() throws Exception {
        if (schema == null) return;
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test void answeringEnvironmentQuestionChangesBasisAndPersistsAudit() {
        var dataSource = new DriverManagerDataSource(url + "?currentSchema=" + schema, user, password);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO \"USER\" (id, name) VALUES (?, 'owner')", owner);
        jdbc.update("INSERT INTO project (id, user_id, name, version, current_step, created_at, updated_at) VALUES (?, ?, 'test', 1, 'RECOMMENDATION', now(), now())", project, owner);
        jdbc.update("INSERT INTO project_profile (id, project_id, kind, version, field_metadata, confirmed_by, confirmed_at, created_at, updated_at) VALUES (?, ?, 'CONFIRMED', 1, '{}'::jsonb, ?, now(), now(), now())", profile, project, owner);
        jdbc.update("INSERT INTO profile_confirmation_event (id, project_id, confirmed_profile_id, confirmed_profile_version, project_version, actor_id, confirmed_at, request_id, profile_snapshot) VALUES (?, ?, ?, 1, 1, ?, now(), 'answer-test', '{}'::jsonb)", confirmation, project, profile, owner);
        jdbc.update("INSERT INTO recommendation (id, project_id, confirmed_profile_id, confirmed_profile_version, confirmation_event_id, project_version, review_version, developer_version, environment_version, catalog_release, catalog_hash, status, evidence_level, created_at) VALUES (?, ?, ?, 1, ?, 1, 0, 0, 0, 'test', ?, 'NEEDS_INFORMATION', 'PLAN_ONLY', now())",
            recommendation, project, profile, confirmation, "a".repeat(64));
        jdbc.update("INSERT INTO recommendation_notice (id, recommendation_id, type, question_owner, answer_target, code, message) VALUES (?, ?, 'QUESTION', 'B', 'environment.target', 'environment_target', 'Which client?')", notice, recommendation);

        var latest = new RecommendationLatestDetailsService(ignored -> null, ignored -> null, () -> null) {
            @Override public Snapshot latest(String ignored) {
                long version = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) FROM environment_profile WHERE project_id = ?", Long.class, project);
                var summary = new RecommendationReadModel(recommendation.toString(),
                    RecommendationDecision.Status.NEEDS_INFORMATION,
                    version == 0 ? RecommendationReadModel.Validity.CURRENT : RecommendationReadModel.Validity.STALE,
                    null, List.of());
                return new Snapshot(summary, null, null, List.of(), List.of(), List.of(), List.of());
            }
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<RecommendationLatestDetailsService> latestProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<RecommendationInputsService> inputProvider = mock(ObjectProvider.class);
        when(latestProvider.getIfAvailable()).thenReturn(latest);
        when(inputProvider.getIfAvailable()).thenReturn(new RecommendationInputsService(dataSource));
        var service = new RecommendationQuestionAnswerService(jdbc, latestProvider, inputProvider);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        String answer = "{\"osFamily\":\"WINDOWS\",\"clientId\":\"claude-code\",\"clientVersion\":\"2.1.310\"}";
        transaction.executeWithoutResult(ignored -> service.save(project.toString(), recommendation.toString(),
            notice.toString(), answer, owner));

        assertEquals("2.1.310", jdbc.queryForObject("SELECT client_version FROM environment_profile WHERE project_id = ?", String.class, project));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_question_answer WHERE notice_id = ?", Integer.class, notice));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND event = 'RECOMMENDATION_QUESTION_ANSWERED'", Integer.class, notice));
        assertEquals(RecommendationReadModel.Validity.STALE, latest.latest(project.toString()).summary().validity());
        assertThrows(com.agentfit.coreapi.global.exception.BusinessException.class,
            () -> transaction.executeWithoutResult(ignored -> service.save(project.toString(), recommendation.toString(),
                notice.toString(), answer, owner)));
    }

    @Test void environmentDetailsSurviveSaveReadAndLegacyQuestionUpdate() {
        var dataSource = new DriverManagerDataSource(url + "?currentSchema=" + schema, user, password);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO \"USER\" (id, name) VALUES (?, 'owner')", owner);
        jdbc.update("INSERT INTO project (id, user_id, name, version, current_step, created_at, updated_at) VALUES (?, ?, 'test', 1, 'RECOMMENDATION', now(), now())", project, owner);
        jdbc.update("INSERT INTO project_profile (id, project_id, kind, version, field_metadata, confirmed_by, confirmed_at, created_at, updated_at) VALUES (?, ?, 'CONFIRMED', 1, '{}'::jsonb, ?, now(), now(), now())", profile, project, owner);
        var inputs = new RecommendationInputsService(dataSource);
        assertEquals(1, inputs.saveEnvironment(project.toString(), 0, "WINDOWS", "claude-code", "2.1.310",
            true, List.of(), "11", "HAS_EXISTING", true,
            List.of(new RecommendationInputsService.Runtime("Node.js", "20")),
            List.of(new RecommendationInputsService.AuthReadiness("GitHub", "READY"))));
        var first = inputs.current(project.toString()).environment();
        assertEquals("11", first.osVersion());
        assertEquals("HAS_EXISTING", first.configurationKnowledge());
        assertEquals(List.of(new RecommendationInputsService.Runtime("node.js", "20")), first.runtimes());
        assertEquals(List.of(new RecommendationInputsService.AuthReadiness("github", "READY")), first.authReadiness());
        assertEquals(2, inputs.saveEnvironment(project.toString(), 1, "WINDOWS", "claude-code", "2.1.311", true, List.of()));
        assertEquals(first.runtimes(), inputs.current(project.toString()).environment().runtimes());
        assertEquals(first.authReadiness(), inputs.current(project.toString()).environment().authReadiness());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE project_id = ? AND event = 'ENVIRONMENT_PROFILE_UPDATED'", Integer.class, project));
        assertEquals(1, inputs.saveDeveloper(project.toString(), 0, "FULLSTACK", List.of("API 개발")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE project_id = ? AND event = 'DEVELOPER_PROFILE_UPDATED'", Integer.class, project));
    }
}
