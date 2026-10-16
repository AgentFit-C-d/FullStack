package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.integration.ab.OwnerCheckedSnapshotReaderAdapter;
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
                    "confirmed requirement", List.of("profile.features"))).toList();
            var result = CatalogRecommendationWorkflow.evaluate(catalogDirectory, hash,
                new CatalogRecommendationWorkflow.Request(claims, List.of(),
                    Set.of("profile.features"), false,
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
