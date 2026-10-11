package com.agentfit.coreapi.recommendation.persistence;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.RecommendationDecision;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Writes B1-B4 only after A has locked and owner-checked the current project snapshot. */
public final class JdbcRecommendationMetadataWriter {
    public enum EvidenceLevel { PLAN_ONLY, ENVIRONMENT_DECLARED }
    public enum Necessity { REQUIRED, OPTIONAL, UNDETERMINED }
    public enum ExplanationSource { AI, TEMPLATE }
    public enum NoticeType { QUESTION, REASON }
    public enum QuestionOwner { AI, B }

    public record Capability(String key, Necessity necessity, String reason, List<String> inputRefs) {}
    public record Item(String id, String toolKey, String toolVersion, String supportKey,
                       boolean required, String reason, ExplanationSource explanationSource) {}
    public record Notice(String id, NoticeType type, QuestionOwner questionOwner,
                         String answerTarget, String code, String message, List<String> inputRefs) {}
    public record Draft(String id, PreviewBasis basis, RecommendationDecision.Status status,
                        EvidenceLevel evidenceLevel, Instant createdAt,
                        List<Capability> capabilities, List<Item> items, List<Notice> notices) {}

    public void write(Connection transaction, Draft draft) throws SQLException {
        Objects.requireNonNull(transaction);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("recommendation requires a caller-owned transaction");
        }
        validate(draft);
        UUID id = uuid(draft.id());
        insertRecommendation(transaction, id, draft);
        insertCapabilities(transaction, id, draft.capabilities());
        insertItems(transaction, id, draft.items());
        insertNotices(transaction, id, draft.notices());
    }

    static void validate(Draft draft) {
        if (draft == null || draft.basis() == null || draft.status() == null
            || draft.evidenceLevel() == null || draft.createdAt() == null
            || draft.capabilities() == null || draft.items() == null || draft.notices() == null) {
            throw new IllegalArgumentException("incomplete recommendation metadata");
        }
        PreviewBasis basis = draft.basis();
        uuid(draft.id());
        uuid(basis.projectId());
        uuid(basis.confirmedProfileId());
        uuid(basis.confirmationEventId());
        if (basis.projectVersion() <= 0 || basis.confirmedProfileVersion() <= 0
            || basis.reviewVersion() < 0 || basis.developerVersion() < 0
            || basis.environmentVersion() < 0 || blank(basis.catalogReleaseId())
            || basis.catalogHash() == null || !basis.catalogHash().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid recommendation basis");
        }
        Set<String> keys = new HashSet<>();
        for (Capability capability : draft.capabilities()) {
            if (capability == null || !CapabilityKey.keys().contains(capability.key())
                || !keys.add(capability.key()) || capability.necessity() == null
                || blank(capability.reason())) {
                throw new IllegalArgumentException("invalid Capability metadata");
            }
            requireRefs(capability.inputRefs());
        }
        if (!keys.equals(CapabilityKey.keys())) {
            throw new IllegalArgumentException("all nine Capabilities are required");
        }
        Set<String> toolKeys = new HashSet<>();
        Set<UUID> childIds = new HashSet<>();
        for (Item item : draft.items()) {
            if (item == null || !childIds.add(uuid(item.id())) || blank(item.toolKey())
                || !toolKeys.add(item.toolKey()) || blank(item.toolVersion())
                || blank(item.supportKey()) || blank(item.reason())
                || item.explanationSource() == null) {
                throw new IllegalArgumentException("invalid recommendation item");
            }
        }
        boolean hasQuestion = false;
        boolean hasReason = false;
        for (Notice notice : draft.notices()) {
            if (notice == null || !childIds.add(uuid(notice.id())) || notice.type() == null
                || blank(notice.code()) || blank(notice.message())) {
                throw new IllegalArgumentException("invalid recommendation notice");
            }
            requireRefs(notice.inputRefs());
            if (notice.type() == NoticeType.QUESTION) {
                hasQuestion = true;
                if (notice.questionOwner() == null || blank(notice.answerTarget())) {
                    throw new IllegalArgumentException("question requires owner and answer target");
                }
            } else {
                hasReason = true;
                if (notice.questionOwner() != null || notice.answerTarget() != null) {
                    throw new IllegalArgumentException("reason cannot have question fields");
                }
            }
        }
        if (draft.status() == RecommendationDecision.Status.RECOMMENDED && draft.items().isEmpty()
            || draft.status() != RecommendationDecision.Status.RECOMMENDED && !draft.items().isEmpty()
            || draft.status() == RecommendationDecision.Status.NEEDS_INFORMATION && !hasQuestion
            || (draft.status() == RecommendationDecision.Status.NO_ADDITIONS_NEEDED
                || draft.status() == RecommendationDecision.Status.NO_COMPATIBLE_TOOLS) && !hasReason) {
            throw new IllegalArgumentException("recommendation children do not match outcome");
        }
    }

    private static void requireRefs(List<String> refs) {
        if (refs == null || refs.stream().anyMatch(JdbcRecommendationMetadataWriter::blank)
            || new HashSet<>(refs).size() != refs.size()) {
            throw new IllegalArgumentException("invalid input references");
        }
    }

    private static void insertRecommendation(Connection transaction, UUID id, Draft draft)
        throws SQLException {
        PreviewBasis basis = draft.basis();
        try (PreparedStatement insert = transaction.prepareStatement(
            "INSERT INTO recommendation (id, project_id, confirmed_profile_id, "
                + "confirmed_profile_version, confirmation_event_id, project_version, "
                + "review_version, developer_version, environment_version, catalog_release, "
                + "catalog_hash, status, evidence_level, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setObject(1, id);
            insert.setObject(2, uuid(basis.projectId()));
            insert.setObject(3, uuid(basis.confirmedProfileId()));
            insert.setLong(4, basis.confirmedProfileVersion());
            insert.setObject(5, uuid(basis.confirmationEventId()));
            insert.setLong(6, basis.projectVersion());
            insert.setLong(7, basis.reviewVersion());
            insert.setLong(8, basis.developerVersion());
            insert.setLong(9, basis.environmentVersion());
            insert.setString(10, basis.catalogReleaseId());
            insert.setString(11, basis.catalogHash());
            insert.setString(12, draft.status().name());
            insert.setString(13, draft.evidenceLevel().name());
            insert.setTimestamp(14, Timestamp.from(draft.createdAt()));
            insert.executeUpdate();
        }
    }

    private static void insertCapabilities(Connection transaction, UUID recommendationId,
                                           List<Capability> capabilities) throws SQLException {
        try (PreparedStatement insert = transaction.prepareStatement(
            "INSERT INTO recommendation_capability (recommendation_id, capability_key, "
                + "necessity, reason, input_refs) VALUES (?, ?, ?, ?, ?)")) {
            for (Capability capability : capabilities) {
                insert.setObject(1, recommendationId);
                insert.setString(2, capability.key());
                insert.setString(3, capability.necessity().name());
                insert.setString(4, capability.reason());
                insert.setArray(5, transaction.createArrayOf("text", capability.inputRefs().toArray()));
                insert.executeUpdate();
            }
        }
    }

    private static void insertItems(Connection transaction, UUID recommendationId,
                                    List<Item> items) throws SQLException {
        try (PreparedStatement insert = transaction.prepareStatement(
            "INSERT INTO recommendation_item (id, recommendation_id, tool_key, tool_version, "
                + "support_key, required, reason, explanation_source) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Item item : items) {
                insert.setObject(1, uuid(item.id()));
                insert.setObject(2, recommendationId);
                insert.setString(3, item.toolKey());
                insert.setString(4, item.toolVersion());
                insert.setString(5, item.supportKey());
                insert.setBoolean(6, item.required());
                insert.setString(7, item.reason());
                insert.setString(8, item.explanationSource().name());
                insert.executeUpdate();
            }
        }
    }

    private static void insertNotices(Connection transaction, UUID recommendationId,
                                      List<Notice> notices) throws SQLException {
        try (PreparedStatement insert = transaction.prepareStatement(
            "INSERT INTO recommendation_notice (id, recommendation_id, type, question_owner, "
                + "answer_target, code, message, input_refs) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Notice notice : notices) {
                insert.setObject(1, uuid(notice.id()));
                insert.setObject(2, recommendationId);
                insert.setString(3, notice.type().name());
                insert.setString(4, notice.questionOwner() == null ? null : notice.questionOwner().name());
                insert.setString(5, notice.answerTarget());
                insert.setString(6, notice.code());
                insert.setString(7, notice.message());
                insert.setArray(8, transaction.createArrayOf("text", notice.inputRefs().toArray()));
                insert.executeUpdate();
            }
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("a database ID is not a UUID", exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
