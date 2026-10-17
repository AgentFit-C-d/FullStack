package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.catalog.CatalogRecommendationDetails;
import com.agentfit.coreapi.catalog.CatalogRecommendationWorkflow;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Saves B recommendation metadata only after an A project lock/version recheck. */
@Component
public final class JdbcRecommendationStoreAdapter implements RecommendationCreationService.RecommendationStore {
    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final JdbcRecommendationMetadataWriter writer = new JdbcRecommendationMetadataWriter();

    public JdbcRecommendationStoreAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public String saveIfUnchanged(RecommendationCreationService.OwnerCheckedSnapshot snapshot,
            com.agentfit.coreapi.configuration.preview.PreviewBasis basis,
            CatalogRecommendationWorkflow.Result result) {
        UUID id = UUID.randomUUID();
        return transactions.execute(status -> {
            try {
                var connection = DataSourceUtils.getConnection(dataSource);
                long currentVersion;
                try (var query = connection.prepareStatement("SELECT version FROM project WHERE id = ? FOR UPDATE")) {
                    query.setObject(1, UUID.fromString(snapshot.projectId()));
                    try (var row = query.executeQuery()) {
                        if (!row.next()) throw new RecommendationCreationService.SnapshotChangedException();
                        currentVersion = row.getLong(1);
                    }
                }
                if (currentVersion != snapshot.projectVersion()) {
                    throw new RecommendationCreationService.SnapshotChangedException();
                }
                JdbcRecommendationMetadataWriter.Draft draft = draft(id, basis, result, snapshot);
                writer.write(connection, draft);
                writeSuccessAudit(connection, UUID.fromString(snapshot.projectId()), id,
                    "RECOMMENDATION", "RECOMMENDATION_CREATED");
                return id.toString();
            } catch (SQLException exception) {
                throw new IllegalStateException("recommendation transaction failed", exception);
            }
        });
    }

    private static void writeSuccessAudit(java.sql.Connection connection, UUID projectId,
            UUID targetId, String targetType, String event) throws SQLException {
        try (var insert = connection.prepareStatement("""
            INSERT INTO audit_event (id, project_id, actor_id, event, outcome, target_type, target_id, occurred_at)
            SELECT ?, p.id, p.user_id, ?, 'SUCCEEDED', ?, ?, CURRENT_TIMESTAMP
              FROM project p WHERE p.id = ?
            """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, event);
            insert.setString(3, targetType);
            insert.setObject(4, targetId);
            insert.setObject(5, projectId);
            if (insert.executeUpdate() != 1) throw new SQLException("project owner disappeared before Audit");
        }
    }

    private static JdbcRecommendationMetadataWriter.Draft draft(UUID id,
            com.agentfit.coreapi.configuration.preview.PreviewBasis basis,
            CatalogRecommendationWorkflow.Result result,
            RecommendationCreationService.OwnerCheckedSnapshot snapshot) {
        Map<String, AiCapabilityIntake.Claim> claims = new HashMap<>();
        for (AiCapabilityIntake.Claim claim : result.capabilities()) claims.put(claim.capabilityKey(), claim);
        if (!claims.keySet().equals(com.agentfit.coreapi.recommendation.CapabilityKey.keys())) {
            throw new IllegalArgumentException("recommendation requires all nine capabilities");
        }
        List<JdbcRecommendationMetadataWriter.Capability> capabilities = claims.values().stream().sorted(java.util.Comparator.comparing(AiCapabilityIntake.Claim::capabilityKey))
            .map(claim -> new JdbcRecommendationMetadataWriter.Capability(claim.capabilityKey(),
                JdbcRecommendationMetadataWriter.Necessity.valueOf(claim.need().name()), claim.reason(), claim.sourceFields())).toList();
        List<JdbcRecommendationMetadataWriter.Item> items = result.items().stream().map(item ->
            new JdbcRecommendationMetadataWriter.Item(UUID.randomUUID().toString(), item.toolKey(),
                item.catalogVersion(), item.supportKey(), true, item.name(),
                JdbcRecommendationMetadataWriter.ExplanationSource.TEMPLATE)).toList();
        List<JdbcRecommendationMetadataWriter.Notice> notices = result.clarificationQuestions().stream().map(question ->
            new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
                JdbcRecommendationMetadataWriter.NoticeType.QUESTION,
                JdbcRecommendationMetadataWriter.QuestionOwner.AI, question.field(),
                "AI_QUESTION", question.reason(), List.of(question.field()))).toList();
        return new JdbcRecommendationMetadataWriter.Draft(id.toString(), basis, result.decision().status(),
            snapshot.environment() != null && snapshot.environment().complete()
                ? JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED
                : JdbcRecommendationMetadataWriter.EvidenceLevel.PLAN_ONLY,
            Instant.now(), capabilities, items, notices);
    }
}
