package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads only the confirmed, project-scoped A projection consumed by B. */
@Component
public final class OwnerCheckedSnapshotReaderAdapter
        implements RecommendationCreationService.OwnerCheckedSnapshotReader {
    private final JdbcTemplate jdbc;

    public OwnerCheckedSnapshotReaderAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public RecommendationCreationService.OwnerCheckedSnapshot load(String projectId) {
        UUID project = uuid(projectId);
        Map<String, Object> row = jdbc.queryForMap("""
            SELECT p.version AS project_version, p.name AS project_name,
                   pp.id AS profile_id, pp.version AS profile_version,
                   ce.id AS confirmation_event_id, COALESCE(pr.review_version, 0) AS review_version,
                   dp.version AS developer_version, ep.version AS environment_version,
                   ep.os_family, ep.client_id, ep.client_version, p.current_review_id
              FROM project p
              JOIN project_profile pp ON pp.project_id = p.id AND pp.kind = 'CONFIRMED'
              JOIN profile_confirmation_event ce ON ce.project_id = p.id
                   AND ce.confirmed_profile_id = pp.id
              JOIN developer_profile dp ON dp.project_id = p.id
              JOIN environment_profile ep ON ep.project_id = p.id
              LEFT JOIN profile_review pr ON pr.id = p.current_review_id
             WHERE p.id = ?
             ORDER BY ce.confirmed_at DESC
             LIMIT 1
            """, project);
        Set<String> allowed = new HashSet<>();
        Map<String, String> values = new HashMap<>();
        add(values, allowed, "profile.projectName", row.get("project_name"));
        add(values, allowed, "environment.osFamily", row.get("os_family"));
        add(values, allowed, "environment.clientId", row.get("client_id"));
        add(values, allowed, "environment.clientVersion", row.get("client_version"));
        add(values, allowed, "developer.version", row.get("developer_version"));
        jdbc.query("SELECT field_key, ordinal, name, version FROM project_profile_item WHERE project_profile_id = ? ORDER BY ordinal",
            new Object[] { row.get("profile_id") }, (rs, rowNum) -> {
                String key = "profile.item." + rs.getString("field_key") + "." + rs.getInt("ordinal");
                add(values, allowed, key, rs.getString("name") + (rs.getString("version") == null ? "" : " " + rs.getString("version")));
                return key;
            });
        Map<String, String> installed = new HashMap<>();
        jdbc.query("SELECT normalized_name, version FROM environment_installed_component WHERE environment_profile_id = (SELECT id FROM environment_profile WHERE project_id = ?) ORDER BY ordinal",
            (org.springframework.jdbc.core.RowCallbackHandler)
                rs -> installed.put(rs.getString("normalized_name"), rs.getString("version")), project);
        boolean conflict = jdbc.queryForObject("SELECT COUNT(*) FROM project_profile_gap WHERE project_profile_id = ? AND type = 'CONFLICT'",
            Integer.class, row.get("profile_id")) > 0;
        String currentReview = row.get("current_review_id") == null ? null : row.get("current_review_id").toString();
        if (currentReview == null) throw new IllegalArgumentException("confirmed project review is missing");
        return new RecommendationCreationService.OwnerCheckedSnapshot(projectId,
            number(row, "project_version"), row.get("profile_id").toString(), number(row, "profile_version"),
            row.get("confirmation_event_id").toString(), number(row, "review_version"),
            number(row, "developer_version"), number(row, "environment_version"), allowed, values,
            conflict, new EnvironmentTarget((String) row.get("os_family"), (String) row.get("client_id"),
                (String) row.get("client_version")), installed);
    }

    private static void add(Map<String, String> values, Set<String> allowed, String key, Object value) {
        allowed.add(key);
        if (value != null && !value.toString().isBlank()) values.put(key, value.toString());
    }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static UUID uuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException e) { throw new IllegalArgumentException("project ID must be UUID", e); } }
}
