package com.agentfit.coreapi.integration.ab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.catalog.CatalogRecommendationDetails;
import com.agentfit.coreapi.catalog.CatalogRecommendationWorkflow;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcRecommendationStoreTransactionTest {
    @Test
    void recommendationAndSuccessAuditCommitTogether() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:recommendation-tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createSchema(jdbc);
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        jdbc.update("INSERT INTO \"USER\" (id, name) VALUES (?, ?)", ownerId, "owner");
        jdbc.update("INSERT INTO project (id, user_id, version) VALUES (?, ?, ?)", projectId, ownerId, 4L);

        var basis = new PreviewBasis(projectId.toString(), 4L, UUID.randomUUID().toString(), 2L,
            UUID.randomUUID().toString(), 1L, 1L, 1L, "catalog-1", "a".repeat(64));
        var snapshot = new RecommendationCreationService.OwnerCheckedSnapshot(projectId.toString(), 4L,
            basis.confirmedProfileId(), 2L, basis.confirmationEventId(), 1L, 1L, 1L,
            Set.of("profile.projectName"), Map.of("profile.projectName", "demo"), false,
            new EnvironmentTarget("WINDOWS", "client", "1"), Map.of());
        seedSnapshot(jdbc, projectId, basis);
        var claims = CapabilityKey.keys().stream().map(key ->
            new AiCapabilityIntake.Claim(key, AiCapabilityIntake.Need.REQUIRED, "reason",
                List.of("profile.projectName"))).toList();
        var item = new CatalogRecommendationDetails.Item("tool-1", "Tool", null, null, "1.0",
            "support-1", Set.of(), new com.agentfit.coreapi.catalog.VerificationEvidence(
                "https://example.org/support", java.time.LocalDate.of(2026, 10, 17)), List.of());
        var result = new CatalogRecommendationWorkflow.Result("catalog-1", "a".repeat(64),
            new RecommendationDecision(RecommendationDecision.Status.RECOMMENDED, List.of("tool-1"), List.of(), List.of()),
            claims, List.of(), List.of(item));

        String recommendationId = new JdbcRecommendationStoreAdapter(dataSource)
            .saveIfUnchanged(snapshot, basis, result);

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation WHERE id = ?", Integer.class, UUID.fromString(recommendationId)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE project_id = ? AND event = 'RECOMMENDATION_CREATED'", Integer.class, projectId));

        jdbc.update("UPDATE environment_profile SET version = 2 WHERE project_id = ?", projectId);
        assertThrows(RecommendationCreationService.SnapshotChangedException.class,
            () -> new JdbcRecommendationStoreAdapter(dataSource).saveIfUnchanged(snapshot, basis, result));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation", Integer.class));
    }

    private static void seedSnapshot(JdbcTemplate jdbc, UUID projectId, PreviewBasis basis) {
        jdbc.update("INSERT INTO project_profile (id, project_id, kind, version) VALUES (?, ?, 'CONFIRMED', 2)",
            UUID.fromString(basis.confirmedProfileId()), projectId);
        jdbc.update("INSERT INTO profile_confirmation_event (id, project_id, confirmed_profile_id, confirmed_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
            UUID.fromString(basis.confirmationEventId()), projectId, UUID.fromString(basis.confirmedProfileId()));
        jdbc.update("INSERT INTO developer_profile (project_id, version) VALUES (?, 1)", projectId);
        jdbc.update("INSERT INTO environment_profile (project_id, version) VALUES (?, 1)", projectId);
        UUID reviewId = UUID.randomUUID();
        jdbc.update("INSERT INTO profile_review (id, project_id, target_profile_id, target_profile_version, review_version) VALUES (?, ?, ?, 2, 1)",
            reviewId, projectId, UUID.fromString(basis.confirmedProfileId()));
        jdbc.update("UPDATE project SET current_review_id = ? WHERE id = ?", reviewId, projectId);
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE \"USER\" (id UUID PRIMARY KEY, name VARCHAR(100))");
        jdbc.execute("CREATE TABLE project (id UUID PRIMARY KEY, user_id UUID NOT NULL, version BIGINT NOT NULL, current_review_id UUID)");
        jdbc.execute("CREATE TABLE project_profile (id UUID PRIMARY KEY, project_id UUID, kind VARCHAR(20), version BIGINT)");
        jdbc.execute("CREATE TABLE profile_confirmation_event (id UUID PRIMARY KEY, project_id UUID, confirmed_profile_id UUID, confirmed_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE developer_profile (project_id UUID, version BIGINT)");
        jdbc.execute("CREATE TABLE environment_profile (project_id UUID, version BIGINT)");
        jdbc.execute("CREATE TABLE profile_review (id UUID PRIMARY KEY, project_id UUID, target_profile_id UUID, target_profile_version BIGINT, review_version BIGINT)");
        jdbc.execute("CREATE TABLE recommendation (id UUID PRIMARY KEY, project_id UUID, confirmed_profile_id UUID, confirmed_profile_version BIGINT, confirmation_event_id UUID, project_version BIGINT, review_version BIGINT, developer_version BIGINT, environment_version BIGINT, catalog_release VARCHAR(100), catalog_hash CHAR(64), status VARCHAR(40), evidence_level VARCHAR(40), created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE recommendation_capability (recommendation_id UUID, capability_key VARCHAR(100), necessity VARCHAR(30), reason VARCHAR(200), input_refs VARCHAR ARRAY)");
        jdbc.execute("CREATE TABLE recommendation_item (id UUID PRIMARY KEY, recommendation_id UUID, tool_key VARCHAR(100), tool_version VARCHAR(100), support_key VARCHAR(100), required BOOLEAN, reason VARCHAR(200), explanation_source VARCHAR(30))");
        jdbc.execute("CREATE TABLE recommendation_notice (id UUID PRIMARY KEY, recommendation_id UUID, type VARCHAR(30), question_owner VARCHAR(30), answer_target VARCHAR(100), code VARCHAR(100), message VARCHAR(200), input_refs VARCHAR ARRAY)");
        jdbc.execute("CREATE TABLE audit_event (id UUID PRIMARY KEY, project_id UUID, actor_id UUID, event VARCHAR(100), outcome VARCHAR(30), target_type VARCHAR(50), target_id UUID, occurred_at TIMESTAMP)");
    }
}
