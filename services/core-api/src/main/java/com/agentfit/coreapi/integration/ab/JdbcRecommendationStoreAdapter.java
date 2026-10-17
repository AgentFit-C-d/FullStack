package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.catalog.CatalogRecommendationWorkflow;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.persistence.RecommendationMetadataAssembler;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Saves B recommendation metadata only while its confirmed A/B input versions remain current. */
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
                try (var query = connection.prepareStatement("SELECT version FROM project WHERE id = ? FOR UPDATE")) {
                    query.setObject(1, UUID.fromString(snapshot.projectId()));
                    try (var row = query.executeQuery()) {
                        if (!row.next()) throw new RecommendationCreationService.SnapshotChangedException();
                        if (row.getLong(1) != snapshot.projectVersion()) {
                            throw new RecommendationCreationService.SnapshotChangedException();
                        }
                    }
                }
                if (!matchesSnapshot(connection, snapshot)) {
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

    private static boolean matchesSnapshot(java.sql.Connection connection,
            RecommendationCreationService.OwnerCheckedSnapshot snapshot) throws SQLException {
        try (var query = connection.prepareStatement("""
            SELECT pp.id, pp.version, ce.id, COALESCE(pr.review_version, 0),
                   COALESCE(dp.version, 0), COALESCE(ep.version, 0)
              FROM project p
              JOIN project_profile pp ON pp.project_id = p.id AND pp.kind = 'CONFIRMED'
              JOIN profile_confirmation_event ce ON ce.project_id = p.id
                   AND ce.confirmed_profile_id = pp.id
              LEFT JOIN profile_review pr ON pr.id = p.current_review_id
                   AND pr.target_profile_id = pp.id AND pr.target_profile_version = pp.version
              LEFT JOIN developer_profile dp ON dp.project_id = p.id
              LEFT JOIN environment_profile ep ON ep.project_id = p.id
             WHERE p.id = ?
             ORDER BY ce.confirmed_at DESC
             LIMIT 1
            """)) {
            query.setObject(1, UUID.fromString(snapshot.projectId()));
            try (var row = query.executeQuery()) {
                return row.next()
                    && row.getObject(1).toString().equals(snapshot.confirmedProfileId())
                    && row.getLong(2) == snapshot.confirmedProfileVersion()
                    && row.getObject(3).toString().equals(snapshot.confirmationEventId())
                    && row.getLong(4) == snapshot.reviewVersion()
                    && row.getLong(5) == snapshot.developerVersion()
                    && row.getLong(6) == snapshot.environmentVersion();
            }
        }
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
        List<JdbcRecommendationMetadataWriter.Notice> notices = new ArrayList<>();
        for (String code : result.decision().questionCodes()) {
            if ("undetermined_capability".equals(code)) {
                var question = result.clarificationQuestions().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("AI question required for undetermined Capability"));
                notices.add(new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
                    JdbcRecommendationMetadataWriter.NoticeType.QUESTION,
                    JdbcRecommendationMetadataWriter.QuestionOwner.AI, question.field(), code,
                    question.reason(), List.of(question.field())));
            } else if ("pending_conflict".equals(code)) {
                notices.add(new JdbcRecommendationMetadataWriter.Notice(UUID.randomUUID().toString(),
                    JdbcRecommendationMetadataWriter.NoticeType.QUESTION,
                    JdbcRecommendationMetadataWriter.QuestionOwner.B, "profile.review", code,
                    "확정된 기획의 충돌 항목을 확인해 주세요.", List.of()));
            }
        }
        return RecommendationMetadataAssembler.assemble(id.toString(), basis, result,
            snapshot.environment() != null && snapshot.environment().complete()
                ? JdbcRecommendationMetadataWriter.EvidenceLevel.ENVIRONMENT_DECLARED
                : JdbcRecommendationMetadataWriter.EvidenceLevel.PLAN_ONLY,
            Instant.now(), notices, () -> UUID.randomUUID().toString());
    }
}
