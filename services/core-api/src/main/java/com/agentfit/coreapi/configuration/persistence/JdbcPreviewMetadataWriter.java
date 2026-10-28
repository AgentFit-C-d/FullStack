package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.persistence.ProjectDbId;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Writes only B5-B8 metadata using A's already owner-checked, project-locked transaction.
 * The caller must recheck A's current basis, add Audit, and commit or roll back together.
 */
public final class JdbcPreviewMetadataWriter {
    public StoredPreviewState write(Connection transaction, String previewId,
                                    PreviewCreationService.Draft draft,
                                    Instant createdAt, Instant expiresAt) throws SQLException {
        Objects.requireNonNull(transaction);
        Objects.requireNonNull(draft);
        if (transaction.getAutoCommit()) {
            throw new IllegalArgumentException("Preview metadata requires a caller-owned transaction");
        }
        if (createdAt == null || expiresAt == null
            || draft.basis() == null || draft.existingState() == null
            || draft.selectedToolKeys() == null || draft.selectedToolKeys().isEmpty()
            || draft.fileMetadata() == null || draft.fileMetadata().isEmpty()) {
            throw new IllegalArgumentException("incomplete Preview metadata");
        }
        // PostgreSQL stores timestamps at microsecond precision. Return the same values it stores.
        createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
        if (!createdAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("Preview expiry must follow creation");
        }
        UUID id = uuid(previewId);
        UUID recommendationId = uuid(draft.recommendationId());
        requireMatchingRecommendation(transaction, recommendationId, draft.basis());
        Map<String, UUID> itemIds = loadSelectedItems(transaction, recommendationId,
            draft.selectedToolKeys());
        if (draft.policies().stream().anyMatch(policy -> policy == null
            || !itemIds.containsKey(policy.toolKey()))) {
            throw new IllegalArgumentException("policy does not belong to a selected tool");
        }
        var files = draft.fileMetadata().stream()
            .map(file -> new PreviewFile(file.targetKey(), file.relativePath(), file.action(),
                file.beforeHash(), file.afterHash(), null, null, null)).toList();
        PreviewFingerprint.requireConsistentExistingState(draft.existingState(), files);
        var hashes = PreviewFingerprint.computeStoredMetadata(draft.basis(),
            draft.recommendationId(), draft.selectedToolKeys(), draft.policies(),
            draft.generatorVersion(), draft.existingState(), files);
        if (!hashes.selectionDigest().equals(draft.selectionDigest())
            || !hashes.contentDigest().equals(draft.contentDigest())
            || !hashes.fingerprint().equals(draft.fingerprint())) {
            throw new IllegalArgumentException("Preview draft metadata and fingerprint differ");
        }

        insertPreview(transaction, id, recommendationId, draft, createdAt, expiresAt);
        Map<String, UUID> selectionIds = insertSelections(transaction, id, draft.selectedToolKeys(), itemIds);
        insertPolicies(transaction, draft.policies(), selectionIds);
        insertFiles(transaction, id, draft.fileMetadata());
        return new StoredPreviewState(id.toString(), draft.basis(), draft.fingerprint(),
            createdAt, expiresAt);
    }

    static void requireMatchingRecommendation(Connection connection, UUID id,
                                              PreviewBasis basis) throws SQLException {
        String sql = "SELECT project_id, confirmed_profile_id, confirmed_profile_version, "
            + "confirmation_event_id, project_version, review_version, developer_version, "
            + "environment_version, catalog_release, catalog_hash, status "
            + "FROM recommendation WHERE id = ? FOR SHARE";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setObject(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next() || !"RECOMMENDED".equals(row.getString("status"))
                    || !row.getObject("project_id", UUID.class)
                        .equals(ProjectDbId.uuid(basis.projectId()))
                    || !row.getObject("confirmed_profile_id", UUID.class)
                        .equals(uuid(basis.confirmedProfileId()))
                    || row.getLong("confirmed_profile_version") != basis.confirmedProfileVersion()
                    || !row.getObject("confirmation_event_id", UUID.class)
                        .equals(uuid(basis.confirmationEventId()))
                    || row.getLong("project_version") != basis.projectVersion()
                    || row.getLong("review_version") != basis.reviewVersion()
                    || row.getLong("developer_version") != basis.developerVersion()
                    || row.getLong("environment_version") != basis.environmentVersion()
                    || !row.getString("catalog_release").equals(basis.catalogReleaseId())
                    || !row.getString("catalog_hash").equals(basis.catalogHash())) {
                    throw new IllegalArgumentException("recommendation is not current for Preview basis");
                }
            }
        }
    }

    private static Map<String, UUID> loadSelectedItems(Connection connection, UUID recommendationId,
                                                        List<String> selected) throws SQLException {
        Set<String> unique = new HashSet<>(selected);
        if (unique.size() != selected.size() || unique.contains(null)) {
            throw new IllegalArgumentException("duplicate or null selected tool");
        }
        Map<String, UUID> matches = new HashMap<>();
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT id, tool_key FROM recommendation_item WHERE recommendation_id = ?")) {
            query.setObject(1, recommendationId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String key = rows.getString("tool_key");
                    if (unique.contains(key)) matches.put(key, rows.getObject("id", UUID.class));
                }
            }
        }
        if (matches.size() != unique.size()) {
            throw new IllegalArgumentException("Preview selection is outside recommendation");
        }
        return matches;
    }

    private static void insertPreview(Connection connection, UUID id, UUID recommendationId,
                                      PreviewCreationService.Draft draft,
                                      Instant createdAt, Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO config_preview (id, recommendation_id, generator_version, "
            + "existing_state, selection_digest, content_digest, fingerprint, created_at, expires_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setObject(1, id);
            insert.setObject(2, recommendationId);
            insert.setString(3, draft.generatorVersion());
            insert.setString(4, draft.existingState().name());
            insert.setString(5, draft.selectionDigest());
            insert.setString(6, draft.contentDigest());
            insert.setString(7, draft.fingerprint());
            insert.setTimestamp(8, Timestamp.from(createdAt));
            insert.setTimestamp(9, Timestamp.from(expiresAt));
            insert.executeUpdate();
        }
    }

    private static Map<String, UUID> insertSelections(Connection connection, UUID previewId,
                                                       List<String> selected,
                                                       Map<String, UUID> itemIds) throws SQLException {
        Map<String, UUID> selectionIds = new HashMap<>();
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO config_selection (id, preview_id, recommendation_item_id) VALUES (?, ?, ?)")) {
            for (String key : selected) {
                UUID selectionId = UUID.randomUUID();
                insert.setObject(1, selectionId);
                insert.setObject(2, previewId);
                insert.setObject(3, itemIds.get(key));
                insert.executeUpdate();
                selectionIds.put(key, selectionId);
            }
        }
        return selectionIds;
    }

    private static void insertPolicies(Connection connection, List<PermissionSelection> policies,
                                       Map<String, UUID> selectionIds) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO config_permission_selection (selection_id, mapping_key, policy) "
                + "VALUES (?, ?, ?)")) {
            for (PermissionSelection policy : policies) {
                insert.setObject(1, selectionIds.get(policy.toolKey()));
                insert.setString(2, policy.mappingKey());
                insert.setString(3, policy.policy().name());
                insert.executeUpdate();
            }
        }
    }

    private static void insertFiles(Connection connection, UUID previewId,
                                    List<PreviewCreationService.FileMetadata> files) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO config_preview_file (id, preview_id, target_key, relative_path, "
                + "action, before_hash, after_hash) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (PreviewCreationService.FileMetadata file : files) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, previewId);
                insert.setString(3, file.targetKey());
                insert.setString(4, file.relativePath());
                insert.setString(5, file.action().name());
                insert.setString(6, file.beforeHash());
                insert.setString(7, file.afterHash());
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
}
