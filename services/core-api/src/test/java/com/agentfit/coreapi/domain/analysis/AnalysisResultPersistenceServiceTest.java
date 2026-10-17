package com.agentfit.coreapi.domain.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentfit.coreapi.domain.analysis.client.AiAnalysisClient;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttemptContext;
import com.agentfit.coreapi.domain.analysis.service.AnalysisResultPersistenceService;
import com.agentfit.coreapi.domain.project.entity.Project;
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

class AnalysisResultPersistenceServiceTest {
    private static final String PROJECT_ID = "00000000-0000-0000-0000-000000000001";

    @Test
    void persistsStructuredProfileAsDraftWithoutASecondDocumentRead() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
            "analysis_generation", 1L, "version", 4L));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(), eq(UUID.fromString(PROJECT_ID))))
            .thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        var service = new AnalysisResultPersistenceService(jdbc, new ObjectMapper(),
            Clock.fixed(now, ZoneOffset.UTC), false);
        var context = new AnalysisAttemptContext("request-1", PROJECT_ID, 1L, "v1",
            now.plusSeconds(60), "PROCESSING");
        var project = new Project(PROJECT_ID, 1L, "AgentFit", 4L, "PROCESSING", 1L, 0L,
            now, now, "BACKEND", 1L);
        var response = new AiAnalysisClient.AnalysisResponse("request-1", 1L, "v1", Map.of(
            "profile", Map.of("project_name", "AgentFit", "features", List.of("추천")),
            "fieldStates", Map.of("project_name", "suggested"),
            "candidates", List.of(), "questions", List.of(), "modelMetadata", Map.of("model", "test")));

        var saved = service.persist(context, project, response);

        assertEquals(PROJECT_ID, saved.projectId());
        assertEquals(1, saved.draftVersion());
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsExpiredOrMismatchedAnalysisContextBeforeDatabaseWrite() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        Instant now = Instant.parse("2026-10-17T00:00:00Z");
        var service = new AnalysisResultPersistenceService(jdbc, new ObjectMapper(),
            Clock.fixed(now, ZoneOffset.UTC), false);
        var project = new Project(PROJECT_ID, 1L, "AgentFit", 4L, "PROCESSING", 1L, 0L,
            now, now, "BACKEND", 1L);
        var response = new AiAnalysisClient.AnalysisResponse("other", 1L, "v1", Map.of("profile", Map.of()));

        assertThrows(IllegalStateException.class, () -> service.persist(
            new AnalysisAttemptContext("request-1", PROJECT_ID, 1L, "v1", now.plusSeconds(60), "PROCESSING"),
            project, response));
        assertThrows(IllegalStateException.class, () -> service.persist(
            new AnalysisAttemptContext("request-1", PROJECT_ID, 1L, "v1", now.minusSeconds(1), "PROCESSING"),
            project, new AiAnalysisClient.AnalysisResponse("request-1", 1L, "v1", Map.of("profile", Map.of()))));
    }
}
