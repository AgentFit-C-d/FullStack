package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentfit.coreapi.configuration.api.ConfigurationApprovalService;
import com.agentfit.coreapi.configuration.api.ConfigurationDownloadRuntimeService;
import com.agentfit.coreapi.configuration.api.ConfigurationUserReportRuntimeService;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import com.agentfit.coreapi.configuration.persistence.JdbcPreviewMetadataWriter;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.integration.ab.OwnerCheckedSnapshotReaderAdapter;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** A UUID snapshot through authenticated B finalization, using a synthetic test Catalog only. */
@EnabledIfEnvironmentVariable(named = "AGENTFIT_TEST_POSTGRES_URL", matches = ".+")
class FinalizationApiV5PostgresTest {
    @TempDir Path catalogDirectory;
    private final UUID userId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID confirmationId = UUID.randomUUID();
    private final UUID recommendationId = UUID.randomUUID();
    private String schema;
    private String url;
    private String user;
    private String password;

    @BeforeEach
    void createSchema() {
        url = System.getenv("AGENTFIT_TEST_POSTGRES_URL");
        user = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit");
        password = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only");
        schema = "b_final_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").load().migrate();
    }

    @AfterEach
    void dropSchema() throws Exception {
        if (schema == null) return;
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void approvedPartialPreviewCanDownloadZipAndPersistsOnlyMetadata() throws Exception {
        String hash = SyntheticCatalogBundle.write(catalogDirectory);
        var catalog = CatalogSemanticParser.load(catalogDirectory);
        var target = new EnvironmentTarget("WINDOWS", "example-client", "1.0");
        var basis = new PreviewBasis(projectId.toString(), 1, profileId.toString(), 1,
            confirmationId.toString(), 0, 1, 1, catalog.release().releaseId(), hash);
        var request = new CatalogPreviewRequest(basis, recommendationId.toString(), target,
            List.of("example-tool"), List.of(new PermissionSelection("example-tool", "read",
                PermissionPolicy.ASK_EACH_TIME)), ExistingState.UNKNOWN, List.of(), "static-v1");
        String previewId;
        String fingerprint;
        try (var connection = DriverManager.getConnection(scopedUrl(), user, password)) {
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
                    + "', now(), 'finalization', '{}'::jsonb)");
                statement.execute("INSERT INTO developer_profile (id, project_id, version, created_at, updated_at) "
                    + "VALUES ('" + UUID.randomUUID() + "', '" + projectId + "', 1, now(), now())");
                statement.execute("INSERT INTO environment_profile (id, project_id, version, source, "
                    + "os_family, client_id, client_version, created_at, updated_at) VALUES ('"
                    + UUID.randomUUID() + "', '" + projectId
                    + "', 1, 'USER', 'WINDOWS', 'example-client', '1.0', now(), now())");
                UUID reviewId = UUID.randomUUID();
                statement.execute("INSERT INTO profile_review (id, project_id, target_profile_id, "
                    + "target_profile_version, review_version, request_id, contract_version, "
                    + "item_count, created_at) VALUES ('" + reviewId + "', '" + projectId
                    + "', '" + profileId + "', 1, 0, 'final-review', 'v1', 0, now())");
                statement.execute("UPDATE project SET current_review_id = '" + reviewId
                    + "' WHERE id = '" + projectId + "'");
            }
            var capabilities = CapabilityKey.keys().stream().sorted().map(key ->
                new JdbcRecommendationMetadataWriter.Capability(key,
                    key.equals("cap_document_reference") || key.equals("cap_browser_verification")
                        ? JdbcRecommendationMetadataWriter.Necessity.REQUIRED
                        : JdbcRecommendationMetadataWriter.Necessity.OPTIONAL,
                    "reason", List.of("profile.projectName"))).toList();
            var items = List.of(new JdbcRecommendationMetadataWriter.Item(UUID.randomUUID().toString(),
                "example-tool", "1.0", "support-1", true, "reason",
                JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE));
            new JdbcRecommendationMetadataWriter().write(connection,
                new JdbcRecommendationMetadataWriter.Draft(recommendationId.toString(), basis,
                    RecommendationDecision.Status.RECOMMENDED,
                    JdbcRecommendationMetadataWriter.EvidenceLevel.PLAN_ONLY, Instant.now(),
                    capabilities, items, List.of()));
            var savedRecommendation = new StoredRecommendationState(recommendationId.toString(),
                basis, RecommendationDecision.Status.RECOMMENDED, List.of("example-tool"));
            var computed = CatalogPreviewAssembler.assemble(catalogDirectory, hash,
                savedRecommendation, basis, target, request);
            var draft = new PreviewCreationService.Draft(basis, recommendationId.toString(),
                request.selectedToolKeys(), request.permissionSelections(), request.existingState(),
                computed.files().stream().map(file -> new PreviewCreationService.FileMetadata(
                    file.targetKey(), file.relativePath(), file.action(), file.beforeHash(),
                    file.afterHash())).toList(), request.generatorVersion(),
                computed.selectionDigest(), computed.contentDigest(), computed.fingerprint());
            var preview = new JdbcPreviewMetadataWriter().write(connection,
                UUID.randomUUID().toString(), draft, Instant.now(), Instant.now().plusSeconds(600));
            previewId = preview.previewId();
            fingerprint = preview.fingerprint();
            connection.commit();
        }

        var dataSource = new DriverManagerDataSource(scopedUrl(), user, password);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("approvedCatalog", (RecommendationCreationService.ApprovedCatalogReader)
            () -> new RecommendationCreationService.ApprovedCatalog(catalogDirectory, hash));
        var snapshots = new OwnerCheckedSnapshotReaderAdapter(new JdbcTemplate(dataSource));
        var catalogProvider = beans.getBeanProvider(RecommendationCreationService.ApprovedCatalogReader.class);
        var approvals = new ConfigurationApprovalService(dataSource, snapshots, catalogProvider,
            Clock.systemUTC());
        var downloads = new ConfigurationDownloadRuntimeService(dataSource, snapshots, catalogProvider,
            Clock.systemUTC());
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        var approved = transactions.execute(status -> approvals.approve(projectId.toString(), previewId,
            fingerprint, true, userId));
        assertEquals(List.of("cap_browser_verification"), approved.unmetCapabilityKeys());
        var generated = transactions.execute(status -> downloads.download(projectId.toString(),
            approved.approvalId(), previewId, fingerprint, request, userId));
        assertTrue(generated.zipBytes().length > 0);
        assertEquals(List.of("cap_browser_verification"), generated.unmetCapabilityKeys());
        var reports = new ConfigurationUserReportRuntimeService(dataSource, Clock.systemUTC());
        var report = transactions.execute(status -> reports.report(projectId.toString(),
            generated.exportId(), new ConfigurationUserReport.Submission(
                ConfigurationUserReport.ReportedState.APPLIED, null), userId));
        assertEquals(ConfigurationUserReport.Source.USER, report.source());
        assertEquals(ConfigurationUserReport.ReportedState.APPLIED, report.reportedState());
        assertEquals(3, new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE project_id = ?", Integer.class, projectId));
        assertEquals(userId, new JdbcTemplate(dataSource).queryForObject(
            "SELECT reported_by FROM config_export WHERE id = ?", UUID.class,
            UUID.fromString(generated.exportId())));
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE project_id = ? "
                + "AND event = 'CONFIGURATION_APPLICATION_REPORTED'", Integer.class, projectId));
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM config_export", Integer.class));
        assertThrows(ProjectNotFoundException.class, () -> transactions.execute(status ->
            reports.report(projectId.toString(), generated.exportId(),
                new ConfigurationUserReport.Submission(
                    ConfigurationUserReport.ReportedState.FAILED,
                    ConfigurationUserReport.ReasonCode.UNKNOWN), UUID.randomUUID())));
        assertEquals(3, new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE project_id = ?", Integer.class, projectId));
        assertThrows(ProjectNotFoundException.class, () -> transactions.execute(status ->
            approvals.approve(projectId.toString(), previewId, fingerprint, true, UUID.randomUUID())));
        new JdbcTemplate(dataSource).update("UPDATE environment_profile SET version = 2 "
            + "WHERE project_id = ?", projectId);
        assertThrows(RuntimeException.class, () -> transactions.execute(status ->
            downloads.download(projectId.toString(), approved.approvalId(), previewId,
                fingerprint, request, userId)));
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject(
            "SELECT COUNT(*) FROM config_export", Integer.class));
    }

    private String scopedUrl() {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }
}
