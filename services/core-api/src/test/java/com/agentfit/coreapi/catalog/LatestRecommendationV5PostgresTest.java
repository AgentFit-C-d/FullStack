package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.integration.ab.OwnerCheckedSnapshotReaderAdapter;
import com.agentfit.coreapi.integration.ab.JdbcRecommendationStoreAdapter;
import com.agentfit.coreapi.integration.ab.RecommendationInputsService;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.RecommendationReadModel;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.agentfit.coreapi.recommendation.persistence.JdbcLatestRecommendationPorts;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.persistence.RecommendationMetadataAssembler;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in read roundtrip against A's canonical UUID schema and B's stored recommendation. */
@EnabledIfEnvironmentVariable(named = "AGENTFIT_TEST_POSTGRES_URL", matches = ".+")
class LatestRecommendationV5PostgresTest {
    @TempDir Path catalogDirectory;
    private final UUID userId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID confirmationId = UUID.randomUUID();
    private String schema;
    private String url;
    private String user;
    private String password;

    @BeforeEach
    void createSchema() {
        url = System.getenv("AGENTFIT_TEST_POSTGRES_URL");
        user = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_USER", "agentfit");
        password = System.getenv().getOrDefault("AGENTFIT_TEST_POSTGRES_PASSWORD", "agentfit-local-only");
        schema = "b_latest_" + UUID.randomUUID().toString().replace("-", "");
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
    void roleAndEnvironmentInputsUseOptimisticVersionsAndFeedConfirmedSnapshot() {
        var dataSource = new DriverManagerDataSource(scopedUrl(), user, password);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO \"USER\" (id, name) VALUES (?, ?)", userId, "owner");
        jdbc.update("INSERT INTO project (id, user_id, name, version, current_step, created_at, updated_at) VALUES (?, ?, ?, 1, 'RECOMMENDATION', now(), now())",
            projectId, userId, "demo");
        jdbc.update("INSERT INTO project_profile (id, project_id, kind, version, field_metadata, confirmed_by, confirmed_at, created_at, updated_at) VALUES (?, ?, 'CONFIRMED', 1, '{}'::jsonb, ?, now(), now(), now())",
            profileId, projectId, userId);
        jdbc.update("INSERT INTO profile_confirmation_event (id, project_id, confirmed_profile_id, confirmed_profile_version, project_version, actor_id, confirmed_at, request_id, profile_snapshot) VALUES (?, ?, ?, 1, 1, ?, now(), ?, '{}'::jsonb)",
            confirmationId, projectId, profileId, userId, "inputs-test");

        var inputs = new RecommendationInputsService(dataSource);
        assertEquals(1, inputs.saveDeveloper(projectId.toString(), 0,
            "FULL_STACK", List.of("API 검증", "문서 검색")));
        assertEquals(1, inputs.saveEnvironment(projectId.toString(), 0,
            "WINDOWS", "example-client", "1.0", true, List.of()));
        var snapshot = new OwnerCheckedSnapshotReaderAdapter(jdbc).load(projectId.toString());
        assertEquals(1, snapshot.developerVersion());
        assertEquals(1, snapshot.environmentVersion());
        assertEquals(Map.of(), snapshot.installedToolVersions());
        assertEquals("FULL_STACK", snapshot.aiSourceValues().get("developer.role"));
        assertEquals("API 검증", snapshot.aiSourceValues().get("developer.responsibility.0"));

        assertThrows(com.agentfit.coreapi.global.exception.VersionConflictException.class,
            () -> inputs.saveEnvironment(projectId.toString(), 0,
                "WINDOWS", "example-client", "1.0", true, List.of()));
        assertEquals(2, inputs.saveEnvironment(projectId.toString(), 1,
            "WINDOWS", "example-client", "1.0", true,
            List.of(new RecommendationInputsService.InstalledComponent("example-tool", "1.0"))));
        assertEquals("1.0", new OwnerCheckedSnapshotReaderAdapter(jdbc)
            .load(projectId.toString()).installedToolVersions().get("example-tool"));
        var current = inputs.current(projectId.toString());
        assertEquals(1, current.developer().version());
        assertEquals("FULL_STACK", current.developer().role());
        assertEquals(2, current.environment().version());
        assertEquals("example-tool", current.environment().installedComponents().get(0).toolKey());
    }

    @Test
    void confirmedProfileWithoutOptionalRoleEnvironmentOrReviewIsReadableButDraftIsNot() throws Exception {
        var dataSource = new DriverManagerDataSource(scopedUrl(), user, password);
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO \"USER\" (id, name) VALUES (?, ?)", userId, "owner");
        jdbc.update("INSERT INTO project (id, user_id, name, version, current_step, created_at, updated_at) VALUES (?, ?, ?, 1, 'PLANNING', now(), now())",
            projectId, userId, "demo");
        jdbc.update("INSERT INTO project_profile (id, project_id, kind, version, project_name, project_type, domain, field_metadata, created_at, updated_at) VALUES (?, ?, 'DRAFT', 1, ?, ?, ?, '{}'::jsonb, now(), now())",
            profileId, projectId, "draft", "web", "education");
        var snapshots = new OwnerCheckedSnapshotReaderAdapter(jdbc);
        assertThrows(com.agentfit.coreapi.global.exception.ProfileNotConfirmedException.class,
            () -> snapshots.load(projectId.toString()));

        jdbc.update("DELETE FROM project_profile WHERE id = ?", profileId);
        jdbc.update("INSERT INTO project_profile (id, project_id, kind, version, project_name, project_type, domain, field_metadata, confirmed_by, confirmed_at, created_at, updated_at) VALUES (?, ?, 'CONFIRMED', 1, ?, ?, ?, '{}'::jsonb, ?, now(), now(), now())",
            profileId, projectId, "confirmed", "web", "education", userId);
        jdbc.update("INSERT INTO profile_confirmation_event (id, project_id, confirmed_profile_id, confirmed_profile_version, project_version, actor_id, confirmed_at, request_id, profile_snapshot) VALUES (?, ?, ?, 1, 1, ?, now(), ?, '{}'::jsonb)",
            confirmationId, projectId, profileId, userId, "confirmed-without-b-inputs");

        var snapshot = snapshots.load(projectId.toString());
        assertEquals(0, snapshot.reviewVersion());
        assertEquals(0, snapshot.developerVersion());
        assertEquals(0, snapshot.environmentVersion());
        assertEquals("confirmed", snapshot.aiSourceValues().get("profile.projectName"));
        assertEquals("web", snapshot.aiSourceValues().get("profile.projectType"));
        assertEquals("education", snapshot.aiSourceValues().get("profile.domain"));
        assertEquals(false, snapshot.environment().complete());
        assertEquals(null, snapshot.installedToolVersions());

        String hash = SyntheticCatalogBundle.write(catalogDirectory);
        var service = new RecommendationCreationService(snapshots, input ->
            new RecommendationCreationService.CapabilityAssessment(
                CapabilityKey.keys().stream().sorted().map(key ->
                    new AiCapabilityIntake.Claim(key, AiCapabilityIntake.Need.REQUIRED,
                        "confirmed project", List.of("profile.projectName"))).toList(), List.of()),
            () -> new RecommendationCreationService.ApprovedCatalog(catalogDirectory, hash),
            new JdbcRecommendationStoreAdapter(dataSource));
        var created = service.create(projectId.toString(),
            new RecommendationCreationService.ExpectedVersions(1, 0, 0));
        assertEquals(com.agentfit.coreapi.recommendation.RecommendationDecision.Status.NEEDS_INFORMATION,
            created.result().decision().status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation WHERE id = ?",
            Integer.class, UUID.fromString(created.recommendationId())));

        jdbc.update("INSERT INTO environment_profile (id, project_id, version, source, os_family, client_id, client_version, created_at, updated_at) VALUES (?, ?, 1, 'USER', 'WINDOWS', 'example-client', '1.0', now(), now())",
            UUID.randomUUID(), projectId);
        var withUnverifiedInstallState = snapshots.load(projectId.toString());
        assertEquals(null, withUnverifiedInstallState.installedToolVersions());
        var second = service.create(projectId.toString(),
            new RecommendationCreationService.ExpectedVersions(1, 0, 1));
        assertEquals(List.of("installed_components"), second.result().decision().questionCodes());
    }

    @Test
    void latestReadUsesCurrentUuidBasisAndMarksVersionChangeStale() throws Exception {
        String hash = SyntheticCatalogBundle.write(catalogDirectory);
        var catalog = CatalogSemanticParser.load(catalogDirectory);
        UUID environmentId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
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
                    + "', now(), 'latest-read', '{}'::jsonb)");
                statement.execute("INSERT INTO developer_profile (id, project_id, version, "
                    + "created_at, updated_at) VALUES ('" + UUID.randomUUID() + "', '"
                    + projectId + "', 1, now(), now())");
                statement.execute("INSERT INTO environment_profile (id, project_id, version, source, "
                    + "os_family, client_id, client_version, created_at, updated_at) VALUES ('"
                    + environmentId + "', '" + projectId
                    + "', 1, 'USER', 'WINDOWS', 'example-client', '1.0', now(), now())");
                statement.execute("INSERT INTO profile_review (id, project_id, target_profile_id, "
                    + "target_profile_version, review_version, request_id, contract_version, "
                    + "item_count, created_at) VALUES ('" + reviewId + "', '" + projectId
                    + "', '" + profileId + "', 1, 0, 'latest-review', 'v1', 0, now())");
                statement.execute("UPDATE project SET current_review_id = '" + reviewId
                    + "' WHERE id = '" + projectId + "'");
            }
            var claims = CapabilityKey.keys().stream().sorted().map(key ->
                new AiCapabilityIntake.Claim(key,
                    key.equals("cap_document_reference") ? AiCapabilityIntake.Need.REQUIRED
                        : AiCapabilityIntake.Need.OPTIONAL,
                    "confirmed requirement", List.of("profile.item.features.0"))).toList();
            var result = CatalogRecommendationWorkflow.evaluate(catalogDirectory, hash,
                new CatalogRecommendationWorkflow.Request(claims, List.of(),
                    Set.of("profile.item.features.0"), false,
                    new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of()));
            var basis = new PreviewBasis(projectId.toString(), 1, profileId.toString(), 1,
                confirmationId.toString(), 0, 1, 1, catalog.release().releaseId(), hash);
            var draft = RecommendationMetadataAssembler.assemble(UUID.randomUUID().toString(),
                basis, result, JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED,
                Instant.now(), List.of(), () -> UUID.randomUUID().toString());
            new JdbcRecommendationMetadataWriter().write(connection, draft);
            connection.commit();
        }

        var dataSource = new DriverManagerDataSource(scopedUrl(), user, password);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("approvedCatalog", (RecommendationCreationService.ApprovedCatalogReader)
            () -> new RecommendationCreationService.ApprovedCatalog(catalogDirectory, hash));
        var ports = new JdbcLatestRecommendationPorts(
            new OwnerCheckedSnapshotReaderAdapter(new JdbcTemplate(dataSource)),
            beans.getBeanProvider(RecommendationCreationService.ApprovedCatalogReader.class),
            dataSource);
        var service = new RecommendationLatestDetailsService(ports, ports,
            beans.getBean(RecommendationCreationService.ApprovedCatalogReader.class));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        var current = transaction.execute(status -> service.latest(projectId.toString()));
        assertEquals(RecommendationReadModel.Validity.CURRENT, current.summary().validity());
        assertEquals(1, current.catalogItems().size());
        assertEquals(9, current.capabilities().size());
        assertThrows(RuntimeException.class, () -> transaction.execute(status ->
            service.latest(UUID.randomUUID().toString())));

        try (var connection = DriverManager.getConnection(scopedUrl(), user, password);
             var statement = connection.createStatement()) {
            statement.execute("UPDATE environment_profile SET version = 2 WHERE id = '"
                + environmentId + "'");
        }
        var stale = transaction.execute(status -> service.latest(projectId.toString()));
        assertEquals(RecommendationReadModel.Validity.STALE, stale.summary().validity());
        assertEquals(0, stale.catalogItems().size());
    }

    private String scopedUrl() {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }
}
