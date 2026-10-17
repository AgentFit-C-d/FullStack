package com.agentfit.coreapi.domain.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.service.ProfileConfirmationService;
import com.agentfit.coreapi.global.exception.VersionConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ProfileConfirmationServiceTest {
    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DRAFT = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void confirmationCopiesDraftWithoutDeletingItAndWritesTheConfirmationAudit() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        AuditRecorder audit = org.mockito.Mockito.mock(AuditRecorder.class);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
            "project_version", 7L, "draft_id", DRAFT, "draft_version", 3L,
            "project_name", "AgentFit", "project_type", "web", "domain", "dev",
            "field_metadata", "{}"));
        when(jdbc.query(anyString(), any(Object[].class), org.mockito.ArgumentMatchers.<RowMapper<ProfileConfirmationService.ProfileItem>>any()))
            .thenReturn(List.of(new ProfileConfirmationService.ProfileItem(
                "features", 0, "PLAN", "product", "document search", null, "DOCUMENT", "{}")));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(), eq(PROJECT)))
            .thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        var service = new ProfileConfirmationService(jdbc, audit, new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC), false);
        var result = service.confirm(new AuthenticatedPrincipal(1L, "Alice", null, USER),
            PROJECT.toString(), new ProfileConfirmationService.ConfirmRequest(
                3, "confirm-1", "AgentFit confirmed", null, null, null, null));

        assertEquals(PROJECT.toString(), result.projectId());
        assertEquals(3, result.sourceDraftVersion());
        verify(audit).record(any());
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("DELETE FROM project_profile WHERE"), any(Object[].class));
    }

    @Test
    void staleDraftIsRejectedBeforeAnyWrite() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
            "project_version", 7L, "draft_id", DRAFT, "draft_version", 4L,
            "project_name", "AgentFit", "project_type", "web", "domain", "dev",
            "field_metadata", "{}"));
        var service = new ProfileConfirmationService(jdbc, new AuditRecorder(), new ObjectMapper(),
            Clock.systemUTC(), false);

        assertThrows(VersionConflictException.class, () -> service.confirm(
            new AuthenticatedPrincipal(1L, "Alice", null, USER), PROJECT.toString(),
            new ProfileConfirmationService.ConfirmRequest(3, "confirm-1", null, null, null, null, null)));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
