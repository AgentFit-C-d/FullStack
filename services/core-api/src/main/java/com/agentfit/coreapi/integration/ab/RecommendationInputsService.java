package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.global.exception.ProfileNotConfirmedException;
import com.agentfit.coreapi.global.exception.VersionConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** B-owned user declarations. The controller checks A ownership before calling this service. */
@Service
public final class RecommendationInputsService {
    public record InstalledComponent(String toolKey, String version) {}
    public record Runtime(String name, String version) {}
    public record AuthReadiness(String serviceId, String state) {}
    public record Developer(long version, String role, List<String> responsibilities) {}
    public record Environment(long version, String osFamily, String clientId, String clientVersion,
                              boolean installedComponentsKnown, List<InstalledComponent> installedComponents,
                              String osVersion, String configurationKnowledge, boolean runtimesKnown,
                              List<Runtime> runtimes, List<AuthReadiness> authReadiness) {
        public Environment(long version, String osFamily, String clientId, String clientVersion,
                boolean installedComponentsKnown, List<InstalledComponent> installedComponents) {
            this(version, osFamily, clientId, clientVersion, installedComponentsKnown, installedComponents,
                null, "UNKNOWN", false, List.of(), List.of());
        }
    }
    public record Current(Developer developer, Environment environment) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecommendationInputsService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public Current current(String projectId) {
        UUID id = UUID.fromString(projectId);
        return transactions.execute(status -> {
            requireConfirmedAndLock(id);
            Developer developer = jdbc.query("SELECT version, role, responsibilities FROM developer_profile WHERE project_id = ?",
                rs -> {
                    if (!rs.next()) return null;
                    try {
                        Object raw = rs.getObject(3);
                        List<String> responsibilities = raw == null ? List.of()
                            : mapper.readValue(raw.toString(), new TypeReference<List<String>>() {});
                        return new Developer(rs.getLong(1), rs.getString(2), responsibilities);
                    } catch (JsonProcessingException ex) {
                        throw new IllegalStateException("stored developer responsibilities are invalid", ex);
                    }
                }, id);
            Environment environment = jdbc.query("SELECT id, version, os_family, client_id, client_version, installed_components_knowledge, os_version, configuration_knowledge, runtime_knowledge FROM environment_profile WHERE project_id = ?",
                rs -> {
                    if (!rs.next()) return null;
                    UUID environmentId = (UUID) rs.getObject(1);
                    List<InstalledComponent> components = jdbc.query("SELECT normalized_name, version FROM environment_installed_component WHERE environment_profile_id = ? ORDER BY ordinal",
                        (row, ordinal) -> new InstalledComponent(row.getString(1), row.getString(2)), environmentId);
                    List<Runtime> runtimes = jdbc.query("SELECT name, version FROM environment_runtime WHERE environment_profile_id = ? ORDER BY name",
                        (row, ordinal) -> new Runtime(row.getString(1), row.getString(2)), environmentId);
                    List<AuthReadiness> auth = jdbc.query("SELECT service_id, state FROM environment_auth_readiness WHERE environment_profile_id = ? ORDER BY service_id",
                        (row, ordinal) -> new AuthReadiness(row.getString(1), row.getString(2)), environmentId);
                    return new Environment(rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        !"UNKNOWN".equals(rs.getString(6)), components, rs.getString(7), rs.getString(8),
                        !"UNKNOWN".equals(rs.getString(9)), runtimes, auth);
                }, id);
            return new Current(developer, environment);
        });
    }

    public long saveDeveloper(String projectId, long expectedVersion, String role,
            List<String> responsibilities) {
        if (expectedVersion < 0 || !text(role, 30) || responsibilities == null
            || responsibilities.size() > 30 || responsibilities.stream().anyMatch(value -> !text(value, 200))) {
            throw new IllegalArgumentException("invalid developer profile input");
        }
        String json;
        try {
            json = mapper.writeValueAsString(responsibilities);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid developer responsibilities", ex);
        }
        UUID id = UUID.fromString(projectId);
        return transactions.execute(status -> {
            requireConfirmedAndLock(id);
            Long current = jdbc.query("SELECT version FROM developer_profile WHERE project_id = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L, id);
            if (current != expectedVersion) throw new VersionConflictException(expectedVersion, current);
            Instant now = Instant.now();
            if (current == 0) {
                jdbc.update("INSERT INTO developer_profile (id, project_id, version, role, responsibilities, created_at, updated_at) VALUES (?, ?, 1, ?, CAST(? AS jsonb), ?, ?)",
                    UUID.randomUUID(), id, role, json, Timestamp.from(now), Timestamp.from(now));
            } else {
                jdbc.update("UPDATE developer_profile SET version = version + 1, role = ?, responsibilities = CAST(? AS jsonb), updated_at = ? WHERE project_id = ?",
                    role, json, Timestamp.from(now), id);
            }
            recordInputAudit(id, "DEVELOPER_PROFILE_UPDATED", "DEVELOPER_PROFILE", "developer_profile");
            return current + 1;
        });
    }

    public long saveEnvironment(String projectId, long expectedVersion, String osFamily,
            String clientId, String clientVersion, boolean installedComponentsKnown,
            List<InstalledComponent> components) {
        Environment previous = current(projectId).environment();
        return saveEnvironment(projectId, expectedVersion, osFamily, clientId, clientVersion,
            installedComponentsKnown, components, previous == null ? null : previous.osVersion(),
            previous == null ? "UNKNOWN" : previous.configurationKnowledge(),
            previous != null && previous.runtimesKnown(),
            previous == null ? List.of() : previous.runtimes(),
            previous == null ? List.of() : previous.authReadiness());
    }

    public long saveEnvironment(String projectId, long expectedVersion, String osFamily,
            String clientId, String clientVersion, boolean installedComponentsKnown,
            List<InstalledComponent> components, String osVersion, String configurationKnowledge,
            boolean runtimesKnown, List<Runtime> runtimes, List<AuthReadiness> authReadiness) {
        if (expectedVersion < 0 || !text(osFamily, 30) || !text(clientId, 200)
            || clientVersion != null && !text(clientVersion, 200) || components == null || components.size() > 30
            || !installedComponentsKnown && !components.isEmpty() || osVersion != null && !text(osVersion, 200)
            || !Set.of("UNKNOWN", "USER_SAYS_EMPTY", "HAS_EXISTING").contains(configurationKnowledge)
            || runtimes == null || runtimes.size() > 30 || !runtimesKnown && !runtimes.isEmpty()
            || authReadiness == null || authReadiness.size() > 30) {
            throw new IllegalArgumentException("invalid environment profile input");
        }
        Set<String> keys = new HashSet<>();
        for (InstalledComponent component : components) {
            if (component == null || !text(component.toolKey(), 200)
                || component.version() != null && !text(component.version(), 200)
                || !keys.add(normalizedName(component.toolKey()))) {
                throw new IllegalArgumentException("invalid installed component declaration");
            }
        }
        Set<String> runtimeNames = new HashSet<>();
        for (Runtime runtime : runtimes) {
            if (runtime == null || !text(runtime.name(), 200)
                || runtime.version() != null && !text(runtime.version(), 200)
                || !runtimeNames.add(normalizedName(runtime.name()))) {
                throw new IllegalArgumentException("invalid runtime declaration");
            }
        }
        Set<String> services = new HashSet<>();
        for (AuthReadiness auth : authReadiness) {
            if (auth == null || !text(auth.serviceId(), 200)
                || !Set.of("READY", "NOT_READY", "UNKNOWN").contains(auth.state())
                || !services.add(normalizedName(auth.serviceId()))) {
                throw new IllegalArgumentException("invalid auth readiness declaration");
            }
        }
        UUID id = UUID.fromString(projectId);
        return transactions.execute(status -> {
            requireConfirmedAndLock(id);
            var current = jdbc.query("SELECT id, version FROM environment_profile WHERE project_id = ?",
                rs -> rs.next() ? new EnvironmentRow((UUID) rs.getObject(1), rs.getLong(2)) : null, id);
            long version = current == null ? 0 : current.version();
            if (version != expectedVersion) throw new VersionConflictException(expectedVersion, version);
            UUID environmentId = current == null ? UUID.randomUUID() : current.id();
            Instant now = Instant.now();
            String knowledge = installedComponentsKnown ? "KNOWN" : "UNKNOWN";
            String runtimeKnowledge = runtimesKnown ? "KNOWN" : "UNKNOWN";
            String authKnowledge = authReadiness.isEmpty() ? "UNKNOWN" : "KNOWN";
            if (current == null) {
                jdbc.update("INSERT INTO environment_profile (id, project_id, version, source, os_family, os_version, client_id, client_version, configuration_knowledge, runtime_knowledge, auth_readiness_knowledge, installed_components_knowledge, created_at, updated_at) VALUES (?, ?, 1, 'USER', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    environmentId, id, osFamily, osVersion, clientId, clientVersion, configurationKnowledge,
                    runtimeKnowledge, authKnowledge, knowledge,
                    Timestamp.from(now), Timestamp.from(now));
            } else {
                jdbc.update("UPDATE environment_profile SET version = version + 1, source = 'USER', os_family = ?, os_version = ?, client_id = ?, client_version = ?, configuration_knowledge = ?, runtime_knowledge = ?, auth_readiness_knowledge = ?, installed_components_knowledge = ?, updated_at = ? WHERE id = ?",
                    osFamily, osVersion, clientId, clientVersion, configurationKnowledge, runtimeKnowledge,
                    authKnowledge, knowledge, Timestamp.from(now), environmentId);
                jdbc.update("DELETE FROM environment_installed_component WHERE environment_profile_id = ?",
                    environmentId);
                jdbc.update("DELETE FROM environment_runtime WHERE environment_profile_id = ?", environmentId);
                jdbc.update("DELETE FROM environment_auth_readiness WHERE environment_profile_id = ?", environmentId);
            }
            for (int ordinal = 0; ordinal < components.size(); ordinal++) {
                InstalledComponent component = components.get(ordinal);
                jdbc.update("INSERT INTO environment_installed_component (id, environment_profile_id, ordinal, name, normalized_name, version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), environmentId, ordinal, component.toolKey().trim(), normalizedName(component.toolKey()),
                    component.version(), Timestamp.from(now));
            }
            for (Runtime runtime : runtimes) {
                jdbc.update("INSERT INTO environment_runtime (id, environment_profile_id, name, version, created_at) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), environmentId, normalizedName(runtime.name()), runtime.version(), Timestamp.from(now));
            }
            for (AuthReadiness auth : authReadiness) {
                jdbc.update("INSERT INTO environment_auth_readiness (id, environment_profile_id, service_id, state, created_at) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), environmentId, normalizedName(auth.serviceId()), auth.state(), Timestamp.from(now));
            }
            recordInputAudit(id, "ENVIRONMENT_PROFILE_UPDATED", "ENVIRONMENT_PROFILE", "environment_profile");
            return version + 1;
        });
    }

    private void recordInputAudit(UUID projectId, String event, String targetType, String table) {
        int written = jdbc.update("INSERT INTO audit_event (id, project_id, actor_id, event, outcome, target_type, target_id, occurred_at) "
                + "SELECT ?, p.id, p.user_id, ?, 'SUCCEEDED', ?, t.id, CURRENT_TIMESTAMP "
                + "FROM project p JOIN " + table + " t ON t.project_id = p.id WHERE p.id = ?",
            UUID.randomUUID(), event, targetType, projectId);
        if (written != 1) throw new IllegalStateException("input Audit was not saved");
    }

    private void requireConfirmedAndLock(UUID projectId) {
        Integer exists = jdbc.query("SELECT 1 FROM project WHERE id = ? FOR UPDATE",
            rs -> rs.next() ? 1 : null, projectId);
        if (exists == null) throw new IllegalArgumentException("project not found");
        Integer confirmed = jdbc.queryForObject("SELECT COUNT(*) FROM project_profile WHERE project_id = ? AND kind = 'CONFIRMED'",
            Integer.class, projectId);
        if (confirmed == null || confirmed == 0) throw new ProfileNotConfirmedException();
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank()
            && value.codePointCount(0, value.length()) <= max;
    }
    static String normalizedName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record EnvironmentRow(UUID id, long version) {}
}
