package com.agentfit.coreapi.domain.project.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads only structured Profile projections; source documents are never returned. */
@Component
public final class ProjectProfileReader {
    private final JdbcTemplate jdbc;

    public ProjectProfileReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Profiles read(String projectId) {
        UUID id;
        try { id = UUID.fromString(projectId); }
        catch (RuntimeException ex) { return new Profiles(null, null); }
        Map<String, Object> draft = null;
        Map<String, Object> confirmed = null;
        List<Map<String, Object>> profileRows = jdbc.query("""
            SELECT id, kind, version, project_name, project_type, domain, field_metadata,
                   confirmed_at, source_draft_id, source_draft_version
              FROM project_profile WHERE project_id = ? ORDER BY kind
            """, (rs, row) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                UUID profileId = rs.getObject("id", UUID.class);
                value.put("id", profileId.toString());
                value.put("kind", rs.getString("kind"));
                value.put("version", rs.getLong("version"));
                value.put("projectName", rs.getString("project_name"));
                value.put("projectType", rs.getString("project_type"));
                value.put("domain", rs.getString("domain"));
                value.put("fieldMetadata", text(rs.getObject("field_metadata")));
                if (rs.getTimestamp("confirmed_at") != null) {
                    value.put("confirmedAt", rs.getTimestamp("confirmed_at").toInstant());
                }
                value.put("sourceDraftId", rs.getObject("source_draft_id"));
                value.put("sourceDraftVersion", rs.getObject("source_draft_version"));
                return value;
            }, id);
        for (Map<String, Object> profile : profileRows) {
            UUID profileId = UUID.fromString(profile.get("id").toString());
            profile.put("items", jdbc.query("""
                SELECT field_key, ordinal, category, area, name, version, source, evidence
                  FROM project_profile_item WHERE project_profile_id = ? ORDER BY field_key, ordinal
                """, new Object[] { profileId }, (item, itemRow) -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("fieldKey", item.getString("field_key"));
                    entry.put("ordinal", item.getInt("ordinal"));
                    entry.put("category", item.getString("category"));
                    entry.put("area", item.getString("area"));
                    entry.put("name", item.getString("name"));
                    entry.put("version", item.getString("version"));
                    entry.put("source", item.getString("source"));
                    entry.put("evidence", text(item.getObject("evidence")));
                    return entry;
                }));
            if ("DRAFT".equals(profile.get("kind"))) draft = profile;
            if ("CONFIRMED".equals(profile.get("kind"))) confirmed = profile;
        }
        return new Profiles(confirmed, draft);
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }

    public record Profiles(Object confirmed, Object draft) {}
}
