package com.agentfit.coreapi.recommendation.persistence;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.catalog.CatalogRecommendationWorkflow;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/** B1-B4 are created in A's transaction; temporary A parent tables are rolled back. */
@EnabledIfEnvironmentVariable(named = "AGENTFIT_TEST_POSTGRES_URL", matches = ".+")
class JdbcRecommendationMetadataWriterTest {
    private static final UUID PROJECT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROFILE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVENT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private Connection connection;

    @BeforeEach
    void open() throws Exception {
        connection = DriverManager.getConnection(System.getenv("AGENTFIT_TEST_POSTGRES_URL"),
            System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit"),
            System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only"));
        connection.setAutoCommit(false);
        String schema = "b_recommendation_" + UUID.randomUUID().toString().replace("-", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET LOCAL search_path TO " + schema);
            statement.execute("CREATE TABLE project (id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE project_profile (id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE profile_confirmation_event (id UUID PRIMARY KEY)");
            try (var stream = getClass().getResourceAsStream("/db/postgresql/b-schema.sql")) {
                assertNotNull(stream);
                for (String sql : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
            statement.execute("INSERT INTO project VALUES ('" + PROJECT + "')");
            statement.execute("INSERT INTO project_profile VALUES ('" + PROFILE + "')");
            statement.execute("INSERT INTO profile_confirmation_event VALUES ('" + EVENT + "')");
        }
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    @Test
    void writesCompleteRecommendedMetadataWithoutAiRawResponse() throws Exception {
        UUID id = UUID.randomUUID();
        var draft = recommended(id);
        new JdbcRecommendationMetadataWriter().write(connection, draft);

        try (Statement statement = connection.createStatement()) {
            assertEquals(1, count(statement, "recommendation"));
            assertEquals(9, count(statement, "recommendation_capability"));
            assertEquals(1, count(statement, "recommendation_item"));
            assertEquals(1, count(statement, "recommendation_notice"));
            try (ResultSet row = statement.executeQuery("SELECT status, evidence_level, "
                + "confirmation_event_id, catalog_hash FROM recommendation WHERE id = '" + id + "'")) {
                assertTrue(row.next());
                assertEquals("RECOMMENDED", row.getString(1));
                assertEquals("PLAN_ONLY", row.getString(2));
                assertEquals(EVENT, row.getObject(3, UUID.class));
                assertEquals("a".repeat(64), row.getString(4));
            }
        }
    }

    @Test
    void rejectsMissingCapabilityBeforeAnyInsert() throws Exception {
        var original = recommended(UUID.randomUUID());
        var missing = new JdbcRecommendationMetadataWriter.Draft(original.id(), original.basis(),
            original.status(), original.evidenceLevel(), original.createdAt(),
            original.capabilities().subList(0, 8), original.items(), original.notices());

        assertThrows(IllegalArgumentException.class,
            () -> new JdbcRecommendationMetadataWriter().write(connection, missing));
        try (Statement statement = connection.createStatement()) {
            assertEquals(0, count(statement, "recommendation"));
        }
    }

    @Test
    void rejectsItemForNoAdditionsOutcomeBeforeAnyInsert() throws Exception {
        var original = recommended(UUID.randomUUID());
        var inconsistent = new JdbcRecommendationMetadataWriter.Draft(original.id(), original.basis(),
            RecommendationDecision.Status.NO_ADDITIONS_NEEDED, original.evidenceLevel(),
            original.createdAt(), original.capabilities(), original.items(), original.notices());

        assertThrows(IllegalArgumentException.class,
            () -> new JdbcRecommendationMetadataWriter().write(connection, inconsistent));
        try (Statement statement = connection.createStatement()) {
            assertEquals(0, count(statement, "recommendation"));
        }
    }

    @Test
    void writesNeedsInformationWithQuestionAndNoTools() throws Exception {
        var original = recommended(UUID.randomUUID());
        var question = new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
            JdbcRecommendationMetadataWriter.NoticeType.QUESTION,
            JdbcRecommendationMetadataWriter.QuestionOwner.AI, "profile.constraints",
            "MISSING_SCOPE", "clarify project scope", List.of("profile.constraints"));
        var draft = new JdbcRecommendationMetadataWriter.Draft(original.id(), original.basis(),
            RecommendationDecision.Status.NEEDS_INFORMATION, original.evidenceLevel(),
            original.createdAt(), original.capabilities(), List.of(), List.of(question));

        new JdbcRecommendationMetadataWriter().write(connection, draft);

        try (Statement statement = connection.createStatement()) {
            assertEquals(1, count(statement, "recommendation"));
            assertEquals(9, count(statement, "recommendation_capability"));
            assertEquals(0, count(statement, "recommendation_item"));
            try (ResultSet row = statement.executeQuery(
                "SELECT type, question_owner, answer_target FROM recommendation_notice")) {
                assertTrue(row.next());
                assertEquals("QUESTION", row.getString(1));
                assertEquals("AI", row.getString(2));
                assertEquals("profile.constraints", row.getString(3));
            }
        }
    }

    @Test
    void rejectsQuestionAndNoCompatibleOutcomesWithoutMatchingNotice() throws Exception {
        var original = recommended(UUID.randomUUID());
        for (var status : List.of(RecommendationDecision.Status.NEEDS_INFORMATION,
            RecommendationDecision.Status.NO_COMPATIBLE_TOOLS)) {
            var missing = new JdbcRecommendationMetadataWriter.Draft(UUID.randomUUID().toString(),
                original.basis(), status, original.evidenceLevel(), original.createdAt(),
                original.capabilities(), List.of(), List.of());
            assertThrows(IllegalArgumentException.class,
                () -> new JdbcRecommendationMetadataWriter().write(connection, missing));
        }
        try (Statement statement = connection.createStatement()) {
            assertEquals(0, count(statement, "recommendation"));
        }
    }

    @Test
    void rejectsNoAdditionsOutcomeWithoutItsReasonBeforeAnyInsert() throws Exception {
        var original = recommended(UUID.randomUUID());
        var missing = new JdbcRecommendationMetadataWriter.Draft(original.id(), original.basis(),
            RecommendationDecision.Status.NO_ADDITIONS_NEEDED, original.evidenceLevel(),
            original.createdAt(), original.capabilities(), List.of(), List.of());

        assertThrows(IllegalArgumentException.class,
            () -> new JdbcRecommendationMetadataWriter().write(connection, missing));
        try (Statement statement = connection.createStatement()) {
            assertEquals(0, count(statement, "recommendation"));
        }
    }

    @Test
    void writesAndRestoresBGeneratedNoAdditionsReason() throws Exception {
        var basis = recommended(UUID.randomUUID()).basis();
        var claims = Arrays.stream(CapabilityKey.values()).map(key ->
            new AiCapabilityIntake.Claim(key.key(), AiCapabilityIntake.Need.OPTIONAL,
                "confirmed profile", List.of("profile.features"))).toList();
        var result = new CatalogRecommendationWorkflow.Result(basis.catalogReleaseId(),
            basis.catalogHash(), new RecommendationDecision(
                RecommendationDecision.Status.NO_ADDITIONS_NEEDED, List.of(), List.of(),
                List.of("no_required_capabilities")), claims, List.of(), List.of());
        var draft = RecommendationMetadataAssembler.assemble(UUID.randomUUID().toString(),
            basis, result, JdbcRecommendationMetadataWriter.EvidenceLevel.PLAN_ONLY,
            Instant.parse("2026-10-12T00:00:00Z"), List.of(), () -> UUID.randomUUID().toString());

        new JdbcRecommendationMetadataWriter().write(connection, draft);
        var restored = new JdbcLatestRecommendationReader().latestMetadata(connection,
            PROJECT.toString());

        assertEquals(draft.id(), restored.id());
        assertEquals(List.of("no_required_capabilities"),
            restored.notices().stream().map(JdbcRecommendationMetadataWriter.Notice::code).toList());
        assertTrue(restored.items().isEmpty());
    }

    @Test
    void readsLatestStoredRecommendationWithItsBasisAndTools() throws Exception {
        var writer = new JdbcRecommendationMetadataWriter();
        var earlier = recommended(UUID.randomUUID());
        writer.write(connection, earlier);
        var later = new JdbcRecommendationMetadataWriter.Draft(UUID.randomUUID().toString(),
            earlier.basis(), RecommendationDecision.Status.NO_ADDITIONS_NEEDED,
            earlier.evidenceLevel(), earlier.createdAt().plusSeconds(1),
            earlier.capabilities(), List.of(), List.of(noAdditionsReason()));
        writer.write(connection, later);

        var stored = new JdbcLatestRecommendationReader().latest(connection, PROJECT.toString());

        assertNotNull(stored);
        assertEquals(later.id(), stored.recommendationId());
        assertEquals(RecommendationDecision.Status.NO_ADDITIONS_NEEDED, stored.status());
        assertEquals(later.basis(), stored.basis());
        assertEquals(List.of(), stored.toolKeys());
        assertEquals(RecommendationReadModel.Validity.CURRENT,
            RecommendationReadModel.latest(stored, later.basis()).validity());
    }

    @Test
    void returnsNoLatestRecommendationForProjectWithoutRows() throws Exception {
        assertNull(new JdbcLatestRecommendationReader().latest(connection, PROJECT.toString()));
    }

    @Test
    void readsRecommendedToolKeysForPreviewMembershipCheck() throws Exception {
        var draft = recommended(UUID.randomUUID());
        new JdbcRecommendationMetadataWriter().write(connection, draft);

        var stored = new JdbcLatestRecommendationReader().latest(connection, PROJECT.toString());

        assertEquals(List.of("tool-a"), stored.toolKeys());
        assertEquals(RecommendationDecision.Status.RECOMMENDED, stored.status());
    }

    @Test
    void readsExactOwnedRecommendationEvenAfterNewerRecommendationExists() throws Exception {
        var earlier = recommended(UUID.randomUUID());
        var later = new JdbcRecommendationMetadataWriter.Draft(UUID.randomUUID().toString(),
            earlier.basis(), RecommendationDecision.Status.NO_ADDITIONS_NEEDED,
            earlier.evidenceLevel(), earlier.createdAt().plusSeconds(1),
            earlier.capabilities(), List.of(), List.of(noAdditionsReason()));
        var writer = new JdbcRecommendationMetadataWriter();
        writer.write(connection, earlier);
        writer.write(connection, later);
        var reader = new JdbcLatestRecommendationReader();

        var stored = reader.byId(connection, PROJECT.toString(), earlier.id());

        assertEquals(earlier.id(), stored.recommendationId());
        assertEquals(earlier.basis(), stored.basis());
        assertEquals(List.of("tool-a"), stored.toolKeys());
        assertEquals(later.id(), reader.latest(connection, PROJECT.toString()).recommendationId());
        assertNull(reader.byId(connection, UUID.randomUUID().toString(), earlier.id()));
        assertNull(reader.byId(connection, PROJECT.toString(), UUID.randomUUID().toString()));
    }

    @Test
    void readsCompleteLatestRecommendationMetadataWithoutRerunningAi() throws Exception {
        var draft = recommended(UUID.randomUUID());
        new JdbcRecommendationMetadataWriter().write(connection, draft);

        var stored = new JdbcLatestRecommendationReader().latestMetadata(connection, PROJECT.toString());

        assertNotNull(stored);
        assertEquals(draft.id(), stored.id());
        assertEquals(draft.basis(), stored.basis());
        assertEquals(draft.status(), stored.status());
        assertEquals(draft.evidenceLevel(), stored.evidenceLevel());
        assertEquals(draft.createdAt(), stored.createdAt());
        assertEquals(9, stored.capabilities().size());
        assertEquals(draft.capabilities().stream().map(JdbcRecommendationMetadataWriter.Capability::key)
            .collect(java.util.stream.Collectors.toSet()),
            stored.capabilities().stream().map(JdbcRecommendationMetadataWriter.Capability::key)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(draft.items(), stored.items());
        assertEquals(draft.notices(), stored.notices());
        assertNull(new JdbcLatestRecommendationReader().latestMetadata(
            connection, UUID.randomUUID().toString()));
    }

    @Test
    void rejectsStoredRecommendedResultAfterItsOnlyItemDisappears() throws Exception {
        var draft = recommended(UUID.randomUUID());
        new JdbcRecommendationMetadataWriter().write(connection, draft);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM recommendation_item WHERE recommendation_id = '"
                + draft.id() + "'");
        }

        assertThrows(IllegalArgumentException.class,
            () -> new JdbcLatestRecommendationReader().latestMetadata(connection, PROJECT.toString()));
    }

    @Test
    void readsLatestNeedsInformationQuestionWithoutRecommendedItems() throws Exception {
        var earlier = recommended(UUID.randomUUID());
        var question = new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
            JdbcRecommendationMetadataWriter.NoticeType.QUESTION,
            JdbcRecommendationMetadataWriter.QuestionOwner.AI, "profile.constraints",
            "MISSING_SCOPE", "clarify project scope", List.of("profile.constraints"));
        var later = new JdbcRecommendationMetadataWriter.Draft(UUID.randomUUID().toString(),
            earlier.basis(), RecommendationDecision.Status.NEEDS_INFORMATION,
            earlier.evidenceLevel(), earlier.createdAt().plusSeconds(1),
            earlier.capabilities(), List.of(), List.of(question));
        var writer = new JdbcRecommendationMetadataWriter();
        writer.write(connection, earlier);
        writer.write(connection, later);

        var stored = new JdbcLatestRecommendationReader().latestMetadata(connection, PROJECT.toString());

        assertEquals(later.id(), stored.id());
        assertEquals(RecommendationDecision.Status.NEEDS_INFORMATION, stored.status());
        assertTrue(stored.items().isEmpty());
        assertEquals(List.of(question), stored.notices());
    }

    private static JdbcRecommendationMetadataWriter.Draft recommended(UUID id) {
        var capabilities = Arrays.stream(CapabilityKey.values()).map(key ->
            new JdbcRecommendationMetadataWriter.Capability(key.key(),
                JdbcRecommendationMetadataWriter.Necessity.REQUIRED, "confirmed requirement",
                List.of("profile.features"))).toList();
        return new JdbcRecommendationMetadataWriter.Draft(id.toString(),
            new PreviewBasis(PROJECT.toString(), 1, PROFILE.toString(), 1, EVENT.toString(),
                0, 0, 1, "test-release", "a".repeat(64)),
            RecommendationDecision.Status.RECOMMENDED,
            JdbcRecommendationMetadataWriter.EvidenceLevel.PLAN_ONLY,
            Instant.parse("2026-10-11T05:00:00Z"), capabilities,
            List.of(new JdbcRecommendationMetadataWriter.Item(UUID.randomUUID().toString(),
                "tool-a", "1.0", "support-a", true, "covers required capability",
                JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE)),
            List.of(new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
                JdbcRecommendationMetadataWriter.NoticeType.REASON, null, null,
                "VERIFIED_MATCH", "verified catalog match", List.of())));
    }

    private static JdbcRecommendationMetadataWriter.Notice noAdditionsReason() {
        return new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
            JdbcRecommendationMetadataWriter.NoticeType.REASON, null, null,
            "installed_components_cover_required", "current verified tools cover the requirement",
            List.of());
    }

    private static int count(Statement statement, String table) throws Exception {
        try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
