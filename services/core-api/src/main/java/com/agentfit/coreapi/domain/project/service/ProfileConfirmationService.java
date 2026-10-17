package com.agentfit.coreapi.domain.project.service;

import com.agentfit.coreapi.domain.audit.entity.AuditEvent;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.global.exception.ProfileNotConfirmedException;
import com.agentfit.coreapi.global.exception.VersionConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A confirmation boundary that only handles the structured A DRAFT. */
@Service
public class ProfileConfirmationService {
    private final JdbcTemplate jdbc;
    private final AuditRecorder audit;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean postgres;

    @Autowired
    public ProfileConfirmationService(JdbcTemplate jdbc, AuditRecorder audit,
            ObjectMapper mapper, Clock clock) {
        this(jdbc, audit, mapper, clock, isPostgres(jdbc));
    }

    public ProfileConfirmationService(JdbcTemplate jdbc, AuditRecorder audit,
            ObjectMapper mapper, Clock clock, boolean postgres) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.audit = Objects.requireNonNull(audit);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
        this.postgres = postgres;
    }

    @Transactional
    public Confirmation confirm(AuthenticatedPrincipal principal, String projectId,
            ConfirmRequest request) {
        if (principal == null || principal.userUuid() == null) {
            throw new IllegalArgumentException("UUID authenticated principal is required");
        }
        UUID projectUuid = parseUuid(projectId, "project ID");
        validate(request);
        DraftRow draft = loadDraft(projectUuid, principal.userUuid());
        if (draft.draftVersion() != request.expectedDraftVersion()) {
            throw new VersionConflictException(request.expectedDraftVersion(), draft.draftVersion());
        }

        Instant now = Instant.now(clock);
        ProfileData data = draft.data().withOverrides(request);
        UUID confirmedId = currentConfirmedProfile(projectUuid);
        long confirmedVersion = currentConfirmedVersion(projectUuid, confirmedId) + 1;
        if (confirmedId == null) {
            confirmedId = UUID.randomUUID();
            insertConfirmedProfile(confirmedId, projectUuid, confirmedVersion, data,
                principal.userUuid(), draft, now);
        } else {
            updateConfirmedProfile(confirmedId, confirmedVersion, data, principal.userUuid(), draft, now);
            jdbc.update("DELETE FROM project_profile_item WHERE project_profile_id = ?", confirmedId);
        }
        insertItems(confirmedId, data.items(), now);

        long projectVersion = draft.projectVersion() + 1;
        int updated = jdbc.update("""
            UPDATE project SET version = ?, current_step = 'RECOMMENDATION', updated_at = ?
             WHERE id = ? AND version = ? AND user_id = ?
            """, projectVersion, Timestamp.from(now), projectUuid, draft.projectVersion(), principal.userUuid());
        if (updated != 1) {
            throw new VersionConflictException(draft.projectVersion(), projectVersion);
        }

        UUID eventId = UUID.randomUUID();
        String snapshot = snapshotJson(confirmedId, confirmedVersion, projectVersion, data);
        String sql = """
            INSERT INTO profile_confirmation_event
                (id, project_id, confirmed_profile_id, confirmed_profile_version,
                 project_version, source_draft_id, source_draft_version, actor_id,
                 confirmed_at, request_id, profile_snapshot, candidate_actions)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, %s, %s)
            """.formatted(jsonExpression(), jsonExpression());
        jdbc.update(sql, eventId, projectUuid, confirmedId, confirmedVersion, projectVersion,
            draft.draftId(), draft.draftVersion(), principal.userUuid(), Timestamp.from(now),
            request.requestId(), json(snapshot), json("[]"));

        audit.record(new AuditEvent(principal.userUuid(), projectUuid, "PROFILE_CONFIRMED", "SUCCEEDED",
            now, null, Map.of("requestId", request.requestId(),
                "draftVersion", Long.toString(draft.draftVersion()),
                "confirmedProfileVersion", Long.toString(confirmedVersion))));
        return new Confirmation(projectUuid.toString(), confirmedId.toString(), confirmedVersion,
            eventId.toString(), projectVersion, draft.draftVersion());
    }

    private DraftRow loadDraft(UUID projectId, UUID actorId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                SELECT p.version AS project_version, d.id AS draft_id, d.version AS draft_version,
                       d.project_name, d.project_type, d.domain, d.field_metadata
                  FROM project p
                  JOIN project_profile d ON d.project_id = p.id AND d.kind = 'DRAFT'
                 WHERE p.id = ? AND p.user_id = ?
                 FOR UPDATE
                """, projectId, actorId);
            UUID draftId = asUuid(row.get("draft_id"));
            List<ProfileItem> items = jdbc.query("""
                SELECT field_key, ordinal, category, area, name, version, source, evidence
                  FROM project_profile_item WHERE project_profile_id = ? ORDER BY field_key, ordinal
                """, new Object[] { draftId }, (rs, n) -> new ProfileItem(
                    rs.getString("field_key"), rs.getInt("ordinal"), rs.getString("category"),
                    rs.getString("area"), rs.getString("name"), rs.getString("version"),
                    rs.getString("source"), text(rs.getObject("evidence"))));
            return new DraftRow(asLong(row.get("project_version")), draftId,
                asLong(row.get("draft_version")), new ProfileData(
                    text(row.get("project_name")), text(row.get("project_type")),
                    text(row.get("domain")), text(row.get("field_metadata")), items));
        } catch (EmptyResultDataAccessException ex) {
            throw new ProfileNotConfirmedException();
        }
    }

    private UUID currentConfirmedProfile(UUID projectId) {
        return jdbc.query("SELECT id FROM project_profile WHERE project_id = ? AND kind = 'CONFIRMED' FOR UPDATE",
            (rs, n) -> rs.getObject("id", UUID.class), projectId).stream().findFirst().orElse(null);
    }

    private long currentConfirmedVersion(UUID projectId, UUID profileId) {
        if (profileId == null) return 0;
        return jdbc.queryForObject("SELECT version FROM project_profile WHERE id = ? AND project_id = ?",
            Long.class, profileId, projectId);
    }

    private void insertConfirmedProfile(UUID id, UUID projectId, long version, ProfileData data,
            UUID actor, DraftRow draft, Instant now) {
        String sql = """
            INSERT INTO project_profile
                (id, project_id, kind, version, project_name, project_type, domain, field_metadata,
                 confirmed_by, confirmed_at, source_draft_id, source_draft_version, created_at, updated_at)
            VALUES (?, ?, 'CONFIRMED', ?, ?, ?, ?, %s, ?, ?, ?, ?, ?, ?)
            """.formatted(jsonExpression());
        jdbc.update(sql, id, projectId, version, data.projectName(), data.projectType(), data.domain(),
            json(data.fieldMetadata()), actor, Timestamp.from(now), draft.draftId(), draft.draftVersion(),
            Timestamp.from(now), Timestamp.from(now));
    }

    private void updateConfirmedProfile(UUID id, long version, ProfileData data, UUID actor,
            DraftRow draft, Instant now) {
        String sql = """
            UPDATE project_profile SET version = ?, project_name = ?, project_type = ?, domain = ?,
                   field_metadata = %s, confirmed_by = ?, confirmed_at = ?, source_draft_id = ?,
                   source_draft_version = ?, updated_at = ?
             WHERE id = ? AND kind = 'CONFIRMED'
            """.formatted(jsonExpression());
        jdbc.update(sql, version, data.projectName(), data.projectType(), data.domain(), json(data.fieldMetadata()),
            actor, Timestamp.from(now), draft.draftId(), draft.draftVersion(), Timestamp.from(now), id);
    }

    private void insertItems(UUID profileId, List<ProfileItem> items, Instant now) {
        for (ProfileItem item : items) {
            if (item.fieldKey() == null || item.fieldKey().isBlank() || item.name() == null || item.name().isBlank()
                || item.ordinal() < 0 || item.source() == null || item.evidence() == null) {
                throw new IllegalArgumentException("invalid profile item");
            }
            String sql = """
                INSERT INTO project_profile_item
                    (id, project_profile_id, field_key, ordinal, category, area, name, version,
                     source, evidence, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, %s, ?, ?)
                """.formatted(jsonExpression());
            jdbc.update(sql, UUID.randomUUID(), profileId, item.fieldKey(), item.ordinal(), item.category(),
                item.area(), item.name(), item.version(), item.source(), json(item.evidence()),
                Timestamp.from(now), Timestamp.from(now));
        }
    }

    private String snapshotJson(UUID profileId, long profileVersion, long projectVersion, ProfileData data) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("profileId", profileId.toString());
        snapshot.put("profileVersion", profileVersion);
        snapshot.put("projectVersion", projectVersion);
        snapshot.put("projectName", data.projectName());
        snapshot.put("projectType", data.projectType());
        snapshot.put("domain", data.domain());
        snapshot.put("fieldMetadata", data.fieldMetadata());
        snapshot.put("items", data.items());
        try { return mapper.writeValueAsString(snapshot); }
        catch (Exception ex) { throw new IllegalStateException("confirmed profile snapshot could not be serialized", ex); }
    }

    private String json(String value) { return value == null ? "{}" : value; }
    private String jsonExpression() { return postgres ? "CAST(? AS jsonb)" : "?"; }

    private static boolean isPostgres(JdbcTemplate jdbc) {
        try (var connection = jdbc.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        } catch (Exception ex) { return false; }
    }

    private static void validate(ConfirmRequest request) {
        if (request == null || request.expectedDraftVersion() < 1 || request.requestId() == null
            || request.requestId().isBlank() || request.requestId().length() > 100) {
            throw new IllegalArgumentException("expectedDraftVersion and requestId are required");
        }
    }

    private static UUID parseUuid(String value, String label) {
        try { return UUID.fromString(value); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(label + " must be UUID", ex); }
    }

    private static UUID asUuid(Object value) { return value instanceof UUID u ? u : UUID.fromString(value.toString()); }
    private static long asLong(Object value) { return ((Number) value).longValue(); }
    private static String text(Object value) { return value == null ? null : value.toString(); }

    public record ConfirmRequest(long expectedDraftVersion, String requestId,
            String projectName, String projectType, String domain,
            String fieldMetadata, List<ProfileItem> items) {}

    public record ProfileItem(String fieldKey, int ordinal, String category, String area,
            String name, String version, String source, String evidence) {}

    public record Confirmation(String projectId, String confirmedProfileId,
            long confirmedProfileVersion, String confirmationEventId,
            long projectVersion, long sourceDraftVersion) {}

    private record DraftRow(long projectVersion, UUID draftId, long draftVersion, ProfileData data) {}
    private record ProfileData(String projectName, String projectType, String domain,
            String fieldMetadata, List<ProfileItem> items) {
        ProfileData { items = List.copyOf(items == null ? List.of() : items); }
        ProfileData withOverrides(ConfirmRequest request) {
            return new ProfileData(request.projectName() == null ? projectName : request.projectName(),
                request.projectType() == null ? projectType : request.projectType(),
                request.domain() == null ? domain : request.domain(),
                request.fieldMetadata() == null ? fieldMetadata : request.fieldMetadata(),
                request.items() == null ? items : request.items());
        }
    }
}
