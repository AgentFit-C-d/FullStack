package com.agentfit.coreapi.domain.analysis.service;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists only validated structured A output; source documents never enter this writer. */
@Service
public class AnalysisResultPersistenceService {
    private static final List<String> ITEM_FIELDS = List.of(
        "database", "deployment", "frontend", "backend", "ai", "features", "external_integrations");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean postgres;

    @Autowired
    public AnalysisResultPersistenceService(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this(jdbc, mapper, clock, isPostgres(jdbc));
    }

    public AnalysisResultPersistenceService(JdbcTemplate jdbc, ObjectMapper mapper,
            Clock clock, boolean postgres) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.postgres = postgres;
    }

    @Transactional
    public DraftSaved persist(AnalysisAttemptContext context, Project project,
            AiAnalysisClient.AnalysisResponse response) {
        if (context == null || project == null || response == null || !context.matches(response)
            || !context.isAcceptable(Instant.now(clock)) || !context.projectId().equals(project.id())
            || context.generation() != project.analysisGeneration()) {
            throw new IllegalStateException("analysis result is no longer valid");
        }
        Map<String, Object> result = response.result();
        if (result == null || !(result.get("profile") instanceof Map<?, ?> profile)) {
            throw new IllegalArgumentException("structured analysis profile is required");
        }
        UUID projectId = uuid(project.id());
        Map<String, Object> current = jdbc.queryForMap(
            "SELECT analysis_generation, version FROM project WHERE id = ? FOR UPDATE", projectId);
        if (((Number) current.get("analysis_generation")).longValue() != context.generation()) {
            throw new IllegalStateException("analysis generation changed before persistence");
        }

        ProfileValues values = values(profile);
        String metadata = json(result);
        UUID draftId = existingDraft(projectId);
        long draftVersion = existingDraftVersion(projectId, draftId) + 1;
        Instant now = Instant.now(clock);
        if (draftId == null) {
            draftId = UUID.randomUUID();
            String sql = """
                INSERT INTO project_profile
                    (id, project_id, kind, version, project_name, project_type, domain, field_metadata,
                     created_at, updated_at)
                VALUES (?, ?, 'DRAFT', ?, ?, ?, ?, %s, ?, ?)
                """.formatted(jsonExpression());
            jdbc.update(sql, draftId, projectId, draftVersion, values.projectName(), values.projectType(),
                values.domain(), metadataValue(metadata), Timestamp.from(now), Timestamp.from(now));
        } else {
            String sql = """
                UPDATE project_profile SET version = ?, project_name = ?, project_type = ?, domain = ?,
                       field_metadata = ?, updated_at = ?
                 WHERE id = ? AND kind = 'DRAFT'
                """;
            if (postgres) sql = sql.replace("field_metadata = ?", "field_metadata = CAST(? AS jsonb)");
            jdbc.update(sql, draftVersion, values.projectName(), values.projectType(), values.domain(),
                metadataValue(metadata), Timestamp.from(now), draftId);
            jdbc.update("DELETE FROM project_profile_item WHERE project_profile_id = ?", draftId);
        }
        insertItems(draftId, values.items(), now);
        return new DraftSaved(draftId.toString(), draftVersion, project.id(), context.generation());
    }

    private ProfileValues values(Map<?, ?> profile) {
        String projectName = scalar(profile.get("project_name"));
        String projectType = scalar(profile.get("project_type"));
        String domain = scalar(profile.get("domain"));
        List<ProfileItem> items = new ArrayList<>();
        for (String field : ITEM_FIELDS) {
            Object raw = profile.get(field);
            if (raw == null) continue;
            List<?> values = raw instanceof List<?> list ? list : List.of(raw);
            for (int index = 0; index < values.size(); index++) {
                String name = scalar(values.get(index));
                if (name != null && !name.isBlank()) {
                    items.add(new ProfileItem(field, index, name));
                }
            }
        }
        return new ProfileValues(projectName, projectType, domain, items);
    }

    private void insertItems(UUID draftId, List<ProfileItem> items, Instant now) {
        for (ProfileItem item : items) {
            String sql = """
                INSERT INTO project_profile_item
                    (id, project_profile_id, field_key, ordinal, name, source, evidence, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'DOCUMENT', %s, ?, ?)
                """.formatted(jsonExpression());
            jdbc.update(sql, UUID.randomUUID(), draftId, item.fieldKey(), item.ordinal(), item.name(),
                metadataValue("[]"), Timestamp.from(now), Timestamp.from(now));
        }
    }

    private UUID existingDraft(UUID projectId) {
        return jdbc.query("SELECT id FROM project_profile WHERE project_id = ? AND kind = 'DRAFT' FOR UPDATE",
            (rs, n) -> rs.getObject("id", UUID.class), projectId).stream().findFirst().orElse(null);
    }

    private long existingDraftVersion(UUID projectId, UUID draftId) {
        if (draftId == null) return 0;
        return jdbc.queryForObject("SELECT version FROM project_profile WHERE id = ? AND project_id = ?",
            Long.class, draftId, projectId);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("analysis result is not serializable", ex); }
    }

    private Object metadataValue(String value) { return value; }
    private String jsonExpression() { return postgres ? "CAST(? AS jsonb)" : "?"; }
    private static String scalar(Object value) { return value == null ? null : value instanceof String s ? s : value.toString(); }
    private static UUID uuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException ex) { throw new IllegalArgumentException("project ID must be UUID", ex); } }
    private static boolean isPostgres(JdbcTemplate jdbc) {
        try (var connection = jdbc.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (Exception ex) { return false; }
    }

    public record DraftSaved(String draftId, long draftVersion, String projectId, long generation) {}
    private record ProfileValues(String projectName, String projectType, String domain, List<ProfileItem> items) {}
    private record ProfileItem(String fieldKey, int ordinal, String name) {}
}
