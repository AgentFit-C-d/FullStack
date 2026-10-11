package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.history.ApprovedConfigurationGenerator;
import com.agentfit.coreapi.configuration.history.ConfigurationGenerationHistory;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprintInput;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real PostgreSQL test: no A tables or test data survive the rollback. */
@EnabledIfEnvironmentVariable(named = "AGENTFIT_TEST_POSTGRES_URL", matches = ".+")
class JdbcPreviewMetadataWriterTest {
    private static final UUID PROJECT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROFILE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVENT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID RECOMMENDATION = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ITEM = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private Connection connection;
    private String committedSchema;

    @BeforeEach
    void open() throws Exception {
        connection = DriverManager.getConnection(System.getenv("AGENTFIT_TEST_POSTGRES_URL"),
            System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit"),
            System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only"));
        connection.setAutoCommit(false);
        String schema = "b_writer_" + UUID.randomUUID().toString().replace("-", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET LOCAL search_path TO " + schema);
            statement.execute("CREATE TABLE project (id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE project_profile (id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE profile_confirmation_event (id UUID PRIMARY KEY)");
            try (var stream = getClass().getResourceAsStream("/db/postgresql/b-schema.sql")) {
                assertNotNull(stream);
                String ddl = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                for (String sql : ddl.split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
            statement.execute("INSERT INTO project VALUES ('" + PROJECT + "')");
            statement.execute("INSERT INTO project_profile VALUES ('" + PROFILE + "')");
            statement.execute("INSERT INTO profile_confirmation_event VALUES ('" + EVENT + "')");
            statement.execute("INSERT INTO recommendation VALUES ('" + RECOMMENDATION + "', '"
                + PROJECT + "', '" + PROFILE + "', 1, '" + EVENT
                + "', 1, 0, 0, 1, 'test-release', '" + "a".repeat(64)
                + "', 'RECOMMENDED', 'PLAN_ONLY', now())");
            statement.execute("INSERT INTO recommendation_item VALUES ('" + ITEM + "', '"
                + RECOMMENDATION + "', 'tool-a', '1.0', 'support-a', true, 'reason', 'AI')");
        }
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) {
            connection.rollback();
            if (committedSchema != null) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA " + committedSchema + " CASCADE");
                }
                connection.commit();
            }
            connection.close();
        }
    }

    @Test
    void writesPreviewSelectionsPoliciesAndOnlyFileMetadata() throws Exception {
        UUID previewId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-11T05:00:00Z");
        Instant expiresAt = createdAt.plusSeconds(600);

        var saved = new JdbcPreviewMetadataWriter().write(connection, previewId.toString(),
            draft(basis()), createdAt, expiresAt);

        assertEquals(previewId.toString(), saved.previewId());
        assertEquals(createdAt, saved.createdAt());
        try (Statement statement = connection.createStatement()) {
            assertEquals(1, count(statement, "config_preview"));
            assertEquals(1, count(statement, "config_selection"));
            assertEquals(1, count(statement, "config_permission_selection"));
            assertEquals(1, count(statement, "config_preview_file"));
            try (ResultSet rows = statement.executeQuery(
                "SELECT existing_state, approval_id FROM config_preview")) {
                assertTrue(rows.next());
                assertEquals("UNKNOWN", rows.getString(1));
                assertNull(rows.getObject(2));
            }
            try (ResultSet rows = statement.executeQuery(
                "SELECT target_key, relative_path, action, before_hash, after_hash "
                    + "FROM config_preview_file")) {
                assertTrue(rows.next());
                assertEquals("main", rows.getString(1));
                assertEquals("config/main.txt", rows.getString(2));
                assertEquals("PROPOSAL", rows.getString(3));
                assertNull(rows.getString(4));
                assertEquals("d".repeat(64), rows.getString(5));
            }
        }
    }

    @Test
    void rejectsChangedRecommendationBasisBeforeAnyInsert() throws Exception {
        PreviewBasis changed = new PreviewBasis(PROJECT.toString(), 2, PROFILE.toString(), 1,
            EVENT.toString(), 0, 0, 1, "test-release", "a".repeat(64));
        assertThrows(IllegalArgumentException.class,
            () -> new JdbcPreviewMetadataWriter().write(connection, UUID.randomUUID().toString(),
                draft(changed), Instant.now(), Instant.now().plusSeconds(600)));
        try (Statement statement = connection.createStatement()) {
            assertEquals(0, count(statement, "config_preview"));
        }
    }

    @Test
    void rejectsSelectionThatOmitsRequiredRecommendationItem() throws Exception {
        UUID optionalItem = UUID.randomUUID();
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO recommendation_item VALUES ('" + optionalItem + "', '"
                + RECOMMENDATION + "', 'tool-b', '1.0', 'support-b', false, 'reason', 'AI')");
        }
        var original = draft(basis());
        var onlyOptional = new PreviewCreationService.Draft(original.basis(),
            original.recommendationId(), List.of("tool-b"), List.of(),
            original.existingState(), original.fileMetadata(), original.generatorVersion(),
            original.selectionDigest(), original.contentDigest(), original.fingerprint());

        assertThrows(IllegalArgumentException.class,
            () -> new JdbcPreviewMetadataWriter().write(connection, UUID.randomUUID().toString(),
                onlyOptional, Instant.now(), Instant.now().plusSeconds(600)));
        try (Statement statement = connection.createStatement()) {
            assertEquals(0, count(statement, "config_preview"));
        }
    }

    @Test
    void storesApprovalOnlyOnceAgainstExactPreviewFingerprint() throws Exception {
        UUID previewId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-11T05:00:00Z");
        Instant expiresAt = createdAt.plusSeconds(600);
        var preview = new JdbcPreviewMetadataWriter().write(connection, previewId.toString(),
            draft(basis()), createdAt, expiresAt);
        var issued = new StoredApprovalState(UUID.randomUUID().toString(),
            previewId.toString(), preview.fingerprint(), createdAt.plusSeconds(60),
            createdAt.plusSeconds(300));

        var saved = new JdbcPreviewApprovalWriter().approve(connection, actorId.toString(),
            preview, issued);

        assertEquals(issued, saved);
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                 "SELECT approval_id, approved_by, approved_at, approval_expires_at "
                     + "FROM config_preview WHERE id = '" + previewId + "'")) {
            assertTrue(row.next());
            assertEquals(UUID.fromString(issued.approvalId()), row.getObject(1, UUID.class));
            assertEquals(actorId, row.getObject(2, UUID.class));
            assertEquals(issued.approvedAt(), row.getTimestamp(3).toInstant());
            assertEquals(issued.expiresAt(), row.getTimestamp(4).toInstant());
        }
        var second = new StoredApprovalState(UUID.randomUUID().toString(), previewId.toString(),
            preview.fingerprint(), issued.approvedAt(), issued.expiresAt());
        assertThrows(IllegalStateException.class,
            () -> new JdbcPreviewApprovalWriter().approve(connection, actorId.toString(),
                preview, second));
    }

    @Test
    void readsStoredPreviewAndApprovalWithinOwnerCheckedTransaction() throws Exception {
        UUID previewId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-11T05:00:00Z");
        var preview = new JdbcPreviewMetadataWriter().write(connection, previewId.toString(),
            draft(basis()), createdAt, createdAt.plusSeconds(600));
        var reader = new JdbcStoredPreviewReader();

        var beforeApproval = reader.byPreviewId(connection, PROJECT.toString(), previewId.toString());
        assertEquals(preview, beforeApproval.preview());
        assertNull(beforeApproval.approval());
        assertEquals(RECOMMENDATION.toString(), beforeApproval.recommendationId());

        var issued = new StoredApprovalState(UUID.randomUUID().toString(), previewId.toString(),
            preview.fingerprint(), createdAt.plusSeconds(60), createdAt.plusSeconds(300));
        new JdbcPreviewApprovalWriter().approve(connection, UUID.randomUUID().toString(),
            preview, issued);

        var approved = reader.byApprovalId(connection, PROJECT.toString(), issued.approvalId());
        assertEquals(preview, approved.preview());
        assertEquals(issued, approved.approval());
        assertNull(reader.byPreviewId(connection, UUID.randomUUID().toString(), previewId.toString()));
    }

    @Test
    void storesOnlyGeneratedExportMetadataAfterMatchingApprovedPreview() throws Exception {
        var fixture = approvedGeneratedExport();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());

        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT id, preview_id, fingerprint, "
                 + "generated_at, latest_report_id FROM config_export")) {
            assertTrue(row.next());
            assertEquals(fixture.generationId(), row.getObject("id", UUID.class));
            assertEquals(fixture.previewId(), row.getObject("preview_id", UUID.class));
            assertEquals(fixture.generated().history().fingerprint(), row.getString("fingerprint"));
            assertEquals(fixture.generated().history().createdAt(), row.getTimestamp("generated_at").toInstant());
            assertNull(row.getObject("latest_report_id"));
            assertFalse(row.next());
        }
    }

    @Test
    void rejectsExportWhenStoredFileHashNoLongerMatchesGeneratedZip() throws Exception {
        var fixture = approvedGeneratedExport();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE config_preview_file SET after_hash = '" + "e".repeat(64) + "'");
        }

        assertThrows(IllegalStateException.class,
            () -> new JdbcConfigurationExportWriter().save(connection, fixture.generated().history()));
        try (Statement statement = connection.createStatement()) {
            assertEquals(0, count(statement, "config_export"));
        }
    }

    @Test
    void storesOnlyLatestUserStatementAfterExport() throws Exception {
        var fixture = approvedGeneratedExport();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());
        UUID actorId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        Instant reportedAt = fixture.generated().history().createdAt().plusSeconds(5);
        var accepted = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.FAILED,
                ConfigurationUserReport.ReasonCode.COPY_FAILED), Clock.fixed(reportedAt, ZoneOffset.UTC));

        var saved = new JdbcConfigurationReportWriter().save(connection,
            fixture.generationId().toString(), actorId.toString(), reportId.toString(), accepted);

        assertEquals(reportId.toString(), saved.id());
        assertEquals(fixture.generationId().toString(), saved.configurationId());
        assertEquals(ConfigurationUserReport.Source.USER, saved.source());
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT latest_report_id, reported_state, "
                 + "reason_code, reported_by, reported_at FROM config_export")) {
            assertTrue(row.next());
            assertEquals(reportId, row.getObject(1, UUID.class));
            assertEquals("FAILED", row.getString(2));
            assertEquals("COPY_FAILED", row.getString(3));
            assertEquals(actorId, row.getObject(4, UUID.class));
            assertEquals(reportedAt, row.getTimestamp(5).toInstant());
        }
    }

    @Test
    void rejectsReportPredatingGenerationWithoutChangingExport() throws Exception {
        var fixture = approvedGeneratedExport();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());
        var accepted = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null),
            Clock.fixed(fixture.generated().history().createdAt().minusSeconds(1), ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class, () -> new JdbcConfigurationReportWriter().save(
            connection, fixture.generationId().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), accepted));
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT latest_report_id FROM config_export")) {
            assertTrue(row.next());
            assertNull(row.getObject(1));
        }
    }

    @Test
    void newerUserStatementReplacesLatestAndOlderStatementCannotOverwriteIt() throws Exception {
        var fixture = approvedGeneratedExport();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());
        var writer = new JdbcConfigurationReportWriter();
        Instant firstTime = fixture.generated().history().createdAt().plusSeconds(5);
        var first = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.FAILED,
                ConfigurationUserReport.ReasonCode.UNKNOWN), Clock.fixed(firstTime, ZoneOffset.UTC));
        writer.save(connection, fixture.generationId().toString(), UUID.randomUUID().toString(),
            UUID.randomUUID().toString(), first);
        UUID latestId = UUID.randomUUID();
        var latest = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null),
            Clock.fixed(firstTime.plusSeconds(1), ZoneOffset.UTC));
        writer.save(connection, fixture.generationId().toString(), UUID.randomUUID().toString(),
            latestId.toString(), latest);

        assertThrows(IllegalArgumentException.class, () -> writer.save(connection,
            fixture.generationId().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), first));
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                 "SELECT latest_report_id, reported_state, reason_code FROM config_export")) {
            assertTrue(row.next());
            assertEquals(latestId, row.getObject(1, UUID.class));
            assertEquals("APPLIED", row.getString(2));
            assertNull(row.getString(3));
        }
    }

    @Test
    void deletesOnlySpecifiedProjectsBRowsInForeignKeyOrder() throws Exception {
        var fixture = approvedGeneratedExport();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());
        var accepted = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null),
            Clock.fixed(fixture.generated().history().createdAt().plusSeconds(1), ZoneOffset.UTC));
        new JdbcConfigurationReportWriter().save(connection, fixture.generationId().toString(),
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), accepted);

        var deleter = new JdbcProjectBDataDeleter();
        assertEquals(0, deleter.delete(connection, UUID.randomUUID().toString()));
        try (Statement statement = connection.createStatement()) {
            assertEquals(1, count(statement, "config_export"));
        }

        assertEquals(7, deleter.delete(connection, PROJECT.toString()));
        try (Statement statement = connection.createStatement()) {
            for (String table : List.of("config_export", "config_preview_file",
                "config_permission_selection", "config_selection", "config_preview",
                "recommendation_item", "recommendation", "recommendation_capability",
                "recommendation_notice")) {
                assertEquals(0, count(statement, table), table);
            }
            assertEquals(1, count(statement, "project"));
        }
    }

    @Test
    void projectDeletionKeepsOtherProjectsRecommendation() throws Exception {
        UUID otherProject = UUID.randomUUID();
        UUID otherRecommendation = UUID.randomUUID();
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO project VALUES ('" + otherProject + "')");
            statement.execute("INSERT INTO recommendation VALUES ('" + otherRecommendation + "', '"
                + otherProject + "', '" + PROFILE + "', 1, '" + EVENT
                + "', 1, 0, 0, 1, 'test-release', '" + "a".repeat(64)
                + "', 'NO_ADDITIONS_NEEDED', 'PLAN_ONLY', now())");
        }

        assertEquals(2, new JdbcProjectBDataDeleter().delete(connection, PROJECT.toString()));

        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT project_id FROM recommendation")) {
            assertTrue(row.next());
            assertEquals(otherProject, row.getObject(1, UUID.class));
            assertFalse(row.next());
        }
    }

    @Test
    void readsStoredGenerationAndLatestUserReportWithoutConfigurationBytes() throws Exception {
        var fixture = approvedGeneratedExport();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());
        var accepted = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.FAILED,
                ConfigurationUserReport.ReasonCode.AUTH_REQUIRED),
            Clock.fixed(fixture.generated().history().createdAt().plusSeconds(5), ZoneOffset.UTC));
        UUID reportId = UUID.randomUUID();
        new JdbcConfigurationReportWriter().save(connection, fixture.generationId().toString(),
            UUID.randomUUID().toString(), reportId.toString(), accepted);

        var stored = new JdbcConfigurationHistoryReader().find(connection,
            PROJECT.toString(), fixture.generationId().toString());

        assertEquals(fixture.generated().history().fingerprint(), stored.generation().fingerprint());
        assertEquals(fixture.generated().history().files(), stored.generation().files());
        assertEquals(ConfigurationGenerationHistory.VerificationStatus.NOT_RUN,
            stored.generation().verificationStatus());
        assertEquals(reportId.toString(), stored.latestUserReport().id());
        assertEquals(ConfigurationUserReport.Source.USER, stored.latestUserReport().source());
        assertNull(new JdbcConfigurationHistoryReader().find(connection,
            UUID.randomUUID().toString(), fixture.generationId().toString()));
    }

    @Test
    void readsUnreportedGenerationAndRejectsChangedStoredFingerprint() throws Exception {
        var fixture = approvedGeneratedExport();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());
        var reader = new JdbcConfigurationHistoryReader();

        var stored = reader.find(connection, PROJECT.toString(), fixture.generationId().toString());
        assertNotNull(stored);
        assertNull(stored.latestUserReport());
        assertEquals(fixture.generated().history().createdAt(), stored.generation().createdAt());

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE config_export SET fingerprint = '" + "e".repeat(64) + "'");
        }
        assertThrows(IllegalStateException.class,
            () -> reader.find(connection, PROJECT.toString(), fixture.generationId().toString()));
    }

    @Test
    void pagesProjectGenerationHistoryWithoutRepeatingEqualTimestampRows() throws Exception {
        var firstExport = approvedGeneratedExport();
        var secondExport = approvedGeneratedExport();
        var thirdExport = approvedGeneratedExport();
        var writer = new JdbcConfigurationExportWriter();
        writer.save(connection, firstExport.generated().history());
        writer.save(connection, secondExport.generated().history());
        writer.save(connection, thirdExport.generated().history());
        var reader = new JdbcConfigurationHistoryReader();

        var firstPage = reader.list(connection, PROJECT.toString(), null, 2);
        assertEquals(2, firstPage.items().size());
        assertNotNull(firstPage.nextCursor());
        var secondPage = reader.list(connection, PROJECT.toString(), firstPage.nextCursor(), 2);
        assertEquals(1, secondPage.items().size());
        assertNull(secondPage.nextCursor());
        var actualIds = java.util.stream.Stream.concat(firstPage.items().stream(),
            secondPage.items().stream()).map(item -> item.generation().id()).toList();
        assertEquals(3, actualIds.stream().distinct().count());
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                 "SELECT id FROM config_export ORDER BY generated_at DESC, id ASC")) {
            var expectedIds = new java.util.ArrayList<String>();
            while (rows.next()) expectedIds.add(rows.getObject(1, UUID.class).toString());
            assertEquals(expectedIds, actualIds);
        }
        assertTrue(reader.list(connection, UUID.randomUUID().toString(), null, 2).items().isEmpty());
        assertEquals(3, reader.list(connection, PROJECT.toString(), null, 100).items().size());
        assertThrows(IllegalArgumentException.class,
            () -> reader.list(connection, PROJECT.toString(), null, 0));
        assertThrows(IllegalArgumentException.class,
            () -> reader.list(connection, PROJECT.toString(), null, 101));
    }

    @Test
    void historyReadLockPreventsConcurrentProjectDeletionUntilReaderFinishes() throws Exception {
        var fixture = approvedGeneratedExport();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());
        commitSharedSchema();
        try (Connection deleting = secondConnection()) {
            try (Statement statement = deleting.createStatement()) {
                statement.execute("SET LOCAL lock_timeout = '300ms'");
            }
            assertNotNull(new JdbcConfigurationHistoryReader().find(connection,
                PROJECT.toString(), fixture.generationId().toString()));
            SQLException locked = assertThrows(SQLException.class,
                () -> new JdbcProjectBDataDeleter().delete(deleting, PROJECT.toString()));
            assertEquals("55P03", locked.getSQLState());

            deleting.rollback();
            connection.rollback();
            setSearchPath(deleting);
            assertTrue(new JdbcProjectBDataDeleter().delete(deleting, PROJECT.toString()) > 0);
            deleting.rollback();
        }
    }

    @Test
    void concurrentOlderUserReportCannotReplaceCommittedLatestReport() throws Exception {
        var fixture = approvedGeneratedExport();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());
        commitSharedSchema();
        var writer = new JdbcConfigurationReportWriter();
        Instant generatedAt = fixture.generated().history().createdAt();
        UUID latestReportId = UUID.randomUUID();
        var latest = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.APPLIED, null),
            Clock.fixed(generatedAt.plusSeconds(10), ZoneOffset.UTC));
        var older = ConfigurationUserReport.accept(
            new ConfigurationUserReport.Submission(ConfigurationUserReport.ReportedState.FAILED,
                ConfigurationUserReport.ReasonCode.UNKNOWN),
            Clock.fixed(generatedAt.plusSeconds(5), ZoneOffset.UTC));
        writer.save(connection, fixture.generationId().toString(), UUID.randomUUID().toString(),
            latestReportId.toString(), latest);

        try (Connection competing = secondConnection()) {
            try (Statement statement = competing.createStatement()) {
                statement.execute("SET LOCAL lock_timeout = '300ms'");
            }
            SQLException locked = assertThrows(SQLException.class,
                () -> writer.save(competing, fixture.generationId().toString(),
                    UUID.randomUUID().toString(), UUID.randomUUID().toString(), older));
            assertEquals("55P03", locked.getSQLState());

            competing.rollback();
            connection.commit();
            setSearchPath(connection);
            setSearchPath(competing);
            assertThrows(IllegalArgumentException.class,
                () -> writer.save(competing, fixture.generationId().toString(),
                    UUID.randomUUID().toString(), UUID.randomUUID().toString(), older));
            competing.rollback();
            var stored = new JdbcConfigurationHistoryReader().find(connection,
                PROJECT.toString(), fixture.generationId().toString());
            assertEquals(latestReportId.toString(), stored.latestUserReport().id());
            assertEquals(ConfigurationUserReport.ReportedState.APPLIED,
                stored.latestUserReport().reportedState());
        }
    }

    @Test
    void exportSaveLocksApprovedFileMetadataUntilTransactionEnds() throws Exception {
        var fixture = approvedGeneratedExport();
        commitSharedSchema();
        new JdbcConfigurationExportWriter().save(connection, fixture.generated().history());

        try (Connection competing = secondConnection()) {
            try (Statement statement = competing.createStatement()) {
                statement.execute("SET LOCAL lock_timeout = '300ms'");
            }
            SQLException locked = assertThrows(SQLException.class, () -> {
                try (Statement statement = competing.createStatement()) {
                    statement.executeUpdate("UPDATE config_preview_file SET after_hash = '"
                        + "e".repeat(64) + "'");
                }
            });
            assertEquals("55P03", locked.getSQLState());
            competing.rollback();
        }
    }

    private void commitSharedSchema() throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT current_schema()")) {
            assertTrue(row.next());
            committedSchema = row.getString(1);
        }
        connection.commit();
        setSearchPath(connection);
    }

    private Connection secondConnection() throws Exception {
        Connection other = DriverManager.getConnection(System.getenv("AGENTFIT_TEST_POSTGRES_URL"),
            System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit"),
            System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only"));
        other.setAutoCommit(false);
        setSearchPath(other);
        return other;
    }

    private void setSearchPath(Connection target) throws Exception {
        try (Statement statement = target.createStatement()) {
            statement.execute("SET search_path TO " + committedSchema);
        }
    }

    private ExportFixture approvedGeneratedExport() throws Exception {
        Instant now = Instant.parse("2026-10-11T05:02:00Z");
        UUID previewId = UUID.randomUUID();
        UUID generationId = UUID.randomUUID();
        var input = new PreviewFingerprintInput(basis(), RECOMMENDATION.toString(),
            List.of("tool-a"), List.of(new PermissionSelection("tool-a", "read",
                PermissionPolicy.ASK_EACH_TIME)), "static-v1",
            List.of(new PreviewInputFile("main", "config/main.txt", "safe config\n")),
            ExistingState.UNKNOWN, List.of());
        var computed = PreviewFingerprint.compute(input);
        var draft = new PreviewCreationService.Draft(basis(), RECOMMENDATION.toString(),
            input.selectedToolIds(), input.policies(), input.existingState(),
            computed.files().stream().map(file -> new PreviewCreationService.FileMetadata(
                file.targetKey(), file.relativePath(), file.action(), file.beforeHash(), file.afterHash())).toList(),
            input.generatorVersion(), computed.selectionDigest(), computed.contentDigest(),
            computed.fingerprint());
        var preview = new JdbcPreviewMetadataWriter().write(connection, previewId.toString(),
            draft, now.minusSeconds(120), now.plusSeconds(600));
        var approval = new StoredApprovalState(UUID.randomUUID().toString(),
            previewId.toString(), preview.fingerprint(), now.minusSeconds(60), now.plusSeconds(300));
        new JdbcPreviewApprovalWriter().approve(connection, UUID.randomUUID().toString(), preview, approval);
        var generated = ApprovedConfigurationGenerator.generate(preview, approval, basis(),
            approval.approvalId(), previewId.toString(), preview.fingerprint(), input,
            generationId.toString(), Clock.fixed(now, ZoneOffset.UTC));
        return new ExportFixture(previewId, generationId, generated);
    }

    private record ExportFixture(UUID previewId, UUID generationId,
                                 ApprovedConfigurationGenerator.Generated generated) {}

    private static PreviewBasis basis() {
        return new PreviewBasis(PROJECT.toString(), 1, PROFILE.toString(), 1,
            EVENT.toString(), 0, 0, 1, "test-release", "a".repeat(64));
    }

    private static PreviewCreationService.Draft draft(PreviewBasis basis) {
        return new PreviewCreationService.Draft(basis, RECOMMENDATION.toString(),
            List.of("tool-a"), List.of(new PermissionSelection("tool-a", "read",
                PermissionPolicy.ASK_EACH_TIME)), ExistingState.UNKNOWN,
            List.of(new PreviewCreationService.FileMetadata("main", "config/main.txt",
                PreviewFile.Action.PROPOSAL, null, "d".repeat(64))),
            "static-v1", "a".repeat(64), "b".repeat(64), "c".repeat(64));
    }

    private static int count(Statement statement, String table) throws Exception {
        try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
