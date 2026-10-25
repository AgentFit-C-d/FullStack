package com.agentfit.coreapi.configuration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.persistence.JdbcLatestRecommendationReader;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Exercises B against the actual A Phase 2 Flyway schema in an isolated PostgreSQL schema. */
@EnabledIfEnvironmentVariable(named = "AGENTFIT_TEST_POSTGRES_URL", matches = ".+")
class BPhase2UuidSchemaPostgresTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID confirmationId = UUID.randomUUID();
    private final UUID recommendationId = UUID.randomUUID();
    private String schema;
    private Connection connection;

    @BeforeEach
    void open() throws Exception {
        String url = System.getenv("AGENTFIT_TEST_POSTGRES_URL");
        String user = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit");
        String password = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only");
        schema = "b_phase2_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").load().migrate();
        connection = DriverManager.getConnection(url, user, password);
        connection.setSchema(schema);
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.execute("INSERT INTO \"USER\" (id, name) VALUES ('" + userId + "', 'owner')");
            statement.execute("INSERT INTO project (id, user_id, name, version, current_step, "
                + "created_at, updated_at) VALUES ('" + projectId + "', '" + userId
                + "', 'demo', 1, 'RECOMMENDATION', now(), now())");
            statement.execute("INSERT INTO project_profile (id, project_id, kind, version, "
                + "field_metadata, confirmed_by, confirmed_at, created_at, updated_at) VALUES ('"
                + profileId + "', '" + projectId + "', 'CONFIRMED', 1, '{}'::jsonb, '"
                + userId + "', now(), now(), now())");
            statement.execute("INSERT INTO profile_confirmation_event (id, project_id, "
                + "confirmed_profile_id, confirmed_profile_version, project_version, actor_id, "
                + "confirmed_at, request_id, profile_snapshot) VALUES ('" + confirmationId
                + "', '" + projectId + "', '" + profileId + "', 1, 1, '" + userId
                + "', now(), 'b-phase2-test', '{}'::jsonb)");
        }
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
        if (schema != null) {
            String url = System.getenv("AGENTFIT_TEST_POSTGRES_URL");
            String user = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit");
            String password = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only");
            try (var cleanup = DriverManager.getConnection(url, user, password);
                 var statement = cleanup.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void recommendationPreviewApprovalAndDeletionUseUuidColumns() throws Exception {
        PreviewBasis basis = basis();
        new JdbcRecommendationMetadataWriter().write(connection, recommended(basis));
        assertEquals(recommendationId.toString(), new JdbcLatestRecommendationReader()
            .latest(connection, projectId.toString()).recommendationId());

        var input = new PreviewFingerprintInput(basis, recommendationId.toString(),
            List.of("tool-a"), List.of(new PermissionSelection("tool-a", "read",
                PermissionPolicy.ASK_EACH_TIME)), "static-v1",
            List.of(new PreviewInputFile("main", "config/main.txt", "safe config\n")),
            ExistingState.UNKNOWN, List.of());
        var computed = PreviewFingerprint.compute(input);
        var draft = new PreviewCreationService.Draft(basis, recommendationId.toString(),
            input.selectedToolIds(), input.policies(), input.existingState(),
            computed.files().stream().map(file -> new PreviewCreationService.FileMetadata(
                file.targetKey(), file.relativePath(), file.action(), file.beforeHash(),
                file.afterHash())).toList(), input.generatorVersion(),
            computed.selectionDigest(), computed.contentDigest(), computed.fingerprint());
        Instant now = Instant.now();
        var preview = new JdbcPreviewMetadataWriter().write(connection,
            UUID.randomUUID().toString(), draft, now, now.plusSeconds(600));
        assertNotNull(new JdbcStoredPreviewReader().byPreviewId(connection,
            projectId.toString(), preview.previewId()));

        var approval = new StoredApprovalState(UUID.randomUUID().toString(), preview.previewId(),
            preview.fingerprint(), now.plusSeconds(1), now.plusSeconds(300));
        new JdbcPreviewApprovalWriter().approve(connection, userId, preview, approval);
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT approved_by FROM config_preview")) {
            assertTrue(rows.next());
            assertEquals(userId, rows.getObject(1, UUID.class));
        }
        UUID exportId = UUID.randomUUID();
        Instant generatedAt = now.plusSeconds(2);
        var history = ConfigurationGenerationHistory.fromStoredMetadata(
            exportId.toString(), recommendationId.toString(), preview.previewId(),
            approval.approvalId(), basis, preview.fingerprint(), computed.files().stream()
                .map(file -> new ConfigurationGenerationHistory.FileMetadata(
                    file.relativePath(), file.action(), file.afterHash())).toList(), generatedAt);
        new JdbcConfigurationExportWriter().save(connection, history);
        Instant reportedAt = now.plusSeconds(3);
        var accepted = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null),
            Clock.fixed(reportedAt, ZoneOffset.UTC));
        new JdbcConfigurationReportWriter().save(connection, exportId.toString(), userId,
            UUID.randomUUID().toString(), accepted);
        var restored = new JdbcConfigurationHistoryReader().find(connection, projectId.toString(),
            exportId.toString());
        assertNotNull(restored);
        assertEquals(ConfigurationUserReport.ReportedState.APPLIED,
            restored.latestUserReport().reportedState());
        assertTrue(new JdbcProjectBDataDeleter().delete(connection, projectId.toString()) > 0);
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM recommendation")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void failedAuditRollsBackBRecommendationInCallerTransaction() throws Exception {
        new JdbcRecommendationMetadataWriter().write(connection, recommended(basis()));
        assertThrows(SQLException.class, () -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO audit_event (id, project_id, event, outcome, "
                    + "occurred_at) VALUES ('" + UUID.randomUUID() + "', '"
                    + UUID.randomUUID() + "', 'RECOMMENDATION_CREATED', 'SUCCEEDED', now())");
            }
        });
        connection.rollback();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM recommendation")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void migratedSchemaPersistsNotRequestedCapabilityWithoutAQuestion() throws Exception {
        var original = recommended(basis());
        var capabilities = original.capabilities().stream().map(capability ->
            capability.key().equals("cap_document_reference") ? capability
                : new JdbcRecommendationMetadataWriter.Capability(capability.key(),
                    JdbcRecommendationMetadataWriter.Necessity.NOT_REQUESTED,
                    "No matching confirmed work", List.of())).toList();
        var draft = new JdbcRecommendationMetadataWriter.Draft(original.id(), original.basis(),
            original.status(), original.evidenceLevel(), original.createdAt(),
            capabilities, original.items(), List.of());

        new JdbcRecommendationMetadataWriter().write(connection, draft);

        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM recommendation_capability "
                 + "WHERE necessity = 'NOT_REQUESTED'")) {
            assertTrue(rows.next());
            assertEquals(8, rows.getInt(1));
        }
    }

    private PreviewBasis basis() {
        return new PreviewBasis(projectId.toString(), 1, profileId.toString(), 1,
            confirmationId.toString(), 0, 1, 1, "demo-catalog", "a".repeat(64));
    }

    private JdbcRecommendationMetadataWriter.Draft recommended(PreviewBasis basis) {
        var capabilities = CapabilityKey.keys().stream().map(key ->
            new JdbcRecommendationMetadataWriter.Capability(key,
                JdbcRecommendationMetadataWriter.Necessity.REQUIRED, "reason", List.of("profile.projectName")))
            .toList();
        var item = new JdbcRecommendationMetadataWriter.Item(UUID.randomUUID().toString(),
            "tool-a", "1.0", "support-a", true, "reason",
            JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE);
        return new JdbcRecommendationMetadataWriter.Draft(recommendationId.toString(), basis,
            RecommendationDecision.Status.RECOMMENDED,
            JdbcRecommendationMetadataWriter.EvidenceLevel.PLAN_ONLY, Instant.now(),
            capabilities, List.of(item), List.of());
    }
}
