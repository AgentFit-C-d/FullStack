package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.configuration.export.StoredApprovalState;
import com.agentfit.coreapi.configuration.export.PreviewFreshnessGate;
import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.configuration.preview.PreviewFingerprint;
import com.agentfit.coreapi.recommendation.persistence.JdbcLatestRecommendationReader;
import com.agentfit.coreapi.recommendation.persistence.JdbcRecommendationMetadataWriter;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import com.agentfit.coreapi.recommendation.selection.StoredRecommendationState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Reads B's saved download inputs in A's authenticated, project-locked transaction. */
public final class JdbcConfigurationContextReader {
    public record Snapshot(StoredRecommendationState recommendation,
                           StoredPreviewState preview, StoredApprovalState approval,
                           List<PreviewCreationService.StoredItemEvidence> storedItems,
                           Set<String> requiredCapabilityKeys, List<String> selectedToolKeys) {
        public Snapshot {
            storedItems = List.copyOf(storedItems);
            requiredCapabilityKeys = Set.copyOf(requiredCapabilityKeys);
            selectedToolKeys = List.copyOf(selectedToolKeys);
        }
    }

    private final JdbcStoredPreviewReader previewReader = new JdbcStoredPreviewReader();
    private final JdbcLatestRecommendationReader recommendationReader =
        new JdbcLatestRecommendationReader();

    public Snapshot byPreviewId(Connection transaction, String projectId, String previewId)
        throws SQLException {
        return read(transaction, projectId,
            previewReader.byPreviewId(transaction, projectId, previewId));
    }

    public Snapshot byApprovalId(Connection transaction, String projectId, String approvalId)
        throws SQLException {
        return read(transaction, projectId,
            previewReader.byApprovalId(transaction, projectId, approvalId));
    }

    private Snapshot read(Connection transaction, String projectId,
                          JdbcStoredPreviewReader.Snapshot stored) throws SQLException {
        if (stored == null) return null;
        StoredRecommendationState recommendation = recommendationReader.byId(
            transaction, projectId, stored.recommendationId());
        if (recommendation == null || !recommendation.basis().equals(stored.preview().basis())) {
            throw new IllegalStateException("Preview recommendation basis does not match");
        }
        List<JdbcRecommendationMetadataWriter.Item> items = recommendationReader.byIdItems(
            transaction, projectId, stored.recommendationId());
        if (!recommendation.toolKeys().equals(items.stream()
            .map(JdbcRecommendationMetadataWriter.Item::toolKey).toList())) {
            throw new IllegalStateException("Preview recommendation items changed during read");
        }
        List<String> selected = requireIntactMetadata(transaction, stored, recommendation);
        Set<String> required = requiredCapabilities(transaction,
            stored.recommendationId());
        return new Snapshot(recommendation, stored.preview(), stored.approval(),
            items.stream().map(item -> new PreviewCreationService.StoredItemEvidence(
                item.toolKey(), item.toolVersion(), item.supportKey(), item.required())).toList(),
            required, selected);
    }

    private static Set<String> requiredCapabilities(Connection transaction, String recommendationId)
        throws SQLException {
        Set<String> required = new HashSet<>();
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT capability_key FROM recommendation_capability "
                + "WHERE recommendation_id = ? AND necessity = 'REQUIRED' FOR SHARE")) {
            query.setObject(1, UUID.fromString(recommendationId));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) required.add(rows.getString(1));
            }
        }
        return Set.copyOf(required);
    }

    private static List<String> requireIntactMetadata(Connection transaction,
                                                       JdbcStoredPreviewReader.Snapshot stored,
                                                       StoredRecommendationState recommendation)
        throws SQLException {
        UUID previewId = UUID.fromString(stored.preview().previewId());
        String generatorVersion;
        ExistingState existingState;
        String selectionDigest;
        String contentDigest;
        String fingerprint;
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT generator_version, existing_state, selection_digest, content_digest, fingerprint "
                + "FROM config_preview WHERE id = ? FOR SHARE")) {
            query.setObject(1, previewId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("stored Preview disappeared");
                generatorVersion = row.getString(1);
                existingState = ExistingState.valueOf(row.getString(2));
                selectionDigest = row.getString(3);
                contentDigest = row.getString(4);
                fingerprint = row.getString(5);
            }
        }
        List<String> selected = new ArrayList<>();
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT ri.tool_key, ri.recommendation_id FROM config_selection cs "
                + "JOIN recommendation_item ri ON ri.id = cs.recommendation_item_id "
                + "WHERE cs.preview_id = ? FOR SHARE OF cs, ri")) {
            query.setObject(1, previewId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    if (!recommendation.recommendationId().equals(
                        rows.getObject(2, UUID.class).toString())) {
                        throw new IllegalStateException("Preview selection belongs to another recommendation");
                    }
                    selected.add(rows.getString(1));
                }
            }
        }
        List<PermissionSelection> policies = new ArrayList<>();
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT ri.tool_key, cps.mapping_key, cps.policy "
                + "FROM config_permission_selection cps "
                + "JOIN config_selection cs ON cs.id = cps.selection_id "
                + "JOIN recommendation_item ri ON ri.id = cs.recommendation_item_id "
                + "WHERE cs.preview_id = ? FOR SHARE OF cps, cs, ri")) {
            query.setObject(1, previewId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) policies.add(new PermissionSelection(rows.getString(1),
                    rows.getString(2), PermissionPolicy.valueOf(rows.getString(3))));
            }
        }
        List<PreviewFile> files = new ArrayList<>();
        try (PreparedStatement query = transaction.prepareStatement(
            "SELECT target_key, relative_path, action, before_hash, after_hash "
                + "FROM config_preview_file WHERE preview_id = ? FOR SHARE")) {
            query.setObject(1, previewId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) files.add(new PreviewFile(rows.getString(1),
                    rows.getString(2), PreviewFile.Action.valueOf(rows.getString(3)),
                    rows.getString(4), rows.getString(5), null, null, null));
            }
        }
        try {
            PreviewFingerprint.requireConsistentExistingState(existingState, files);
            var hashes = PreviewFingerprint.computeStoredMetadata(stored.preview().basis(),
                recommendation.recommendationId(), selected, policies, generatorVersion,
                existingState, files);
            if (!hashes.selectionDigest().equals(selectionDigest)
                || !hashes.contentDigest().equals(contentDigest)
                || !hashes.fingerprint().equals(fingerprint)
                || !fingerprint.equals(stored.preview().fingerprint())) {
                throw new PreviewFreshnessGate.StalePreviewException(
                    "Preview metadata changed; create a new Preview");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("stored Preview metadata is invalid", exception);
        }
        selected.sort(String::compareTo);
        return List.copyOf(selected);
    }
}
