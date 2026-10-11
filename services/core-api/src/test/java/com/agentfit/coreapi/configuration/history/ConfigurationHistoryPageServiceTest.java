package com.agentfit.coreapi.configuration.history;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.configuration.preview.PreviewFile;
import com.agentfit.coreapi.configuration.report.ConfigurationUserReport;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationHistoryPageServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private static final PreviewBasis CURRENT = new PreviewBasis("project-1", 2,
        "profile-1", 1, "event-1", 0, 0, 2, "catalog-1", "a".repeat(64));
    private static final PreviewBasis OLD = new PreviewBasis("project-1", 1,
        "profile-1", 1, "event-1", 0, 0, 1, "catalog-1", "a".repeat(64));

    @Test
    void projectsEachStoredPageItemAgainstOneOwnerCheckedCurrentBasis() {
        var first = history("generation-1", CURRENT, NOW);
        var second = history("generation-2", OLD, NOW.minusSeconds(1));
        var report = new ConfigurationHistoryReadModel.UserReport("report-1", first.id(),
            ConfigurationUserReport.ReportedState.APPLIED, null,
            ConfigurationUserReport.Source.USER, NOW.plusSeconds(1));
        var cursor = new ConfigurationHistoryPageService.Cursor(second.createdAt(), second.id());
        AtomicInteger basisReads = new AtomicInteger();
        var service = new ConfigurationHistoryPageService(
            projectId -> { basisReads.incrementAndGet(); return CURRENT; },
            (projectId, after, limit) -> new ConfigurationHistoryPageService.StoredPage(
                List.of(new ConfigurationHistoryPageService.StoredItem(first, report),
                    new ConfigurationHistoryPageService.StoredItem(second, null)), cursor));

        var result = service.list("project-1", null, 2);

        assertEquals(1, basisReads.get());
        assertEquals(List.of(ConfigurationGenerationHistory.Validity.CURRENT,
            ConfigurationGenerationHistory.Validity.STALE), result.items().stream()
            .map(ConfigurationHistoryReadModel::validity).toList());
        assertEquals(ConfigurationGenerationHistory.VerificationStatus.NOT_RUN,
            result.items().getFirst().generation().verificationStatus());
        assertEquals(ConfigurationUserReport.Source.USER,
            result.items().getFirst().latestUserReport().source());
        assertEquals(cursor, result.nextCursor());
    }

    @Test
    void rejectsForeignCurrentBasisBeforeLoadingPage() {
        AtomicInteger pageReads = new AtomicInteger();
        var foreign = new PreviewBasis("other-project", 1, "profile-1", 1,
            "event-1", 0, 0, 1, "catalog-1", "a".repeat(64));
        var service = new ConfigurationHistoryPageService(projectId -> foreign,
            (projectId, after, limit) -> {
                pageReads.incrementAndGet();
                return new ConfigurationHistoryPageService.StoredPage(List.of(), null);
            });

        assertThrows(IllegalArgumentException.class,
            () -> service.list("project-1", null, 20));
        assertEquals(0, pageReads.get());
    }

    @Test
    void rejectsForeignGenerationInAnOwnerCheckedPage() {
        var foreign = new PreviewBasis("other-project", 1, "profile-1", 1,
            "event-1", 0, 0, 1, "catalog-1", "a".repeat(64));
        var service = new ConfigurationHistoryPageService(projectId -> CURRENT,
            (projectId, after, limit) -> new ConfigurationHistoryPageService.StoredPage(
                List.of(new ConfigurationHistoryPageService.StoredItem(
                    history("generation-1", foreign, NOW), null)), null));

        assertThrows(IllegalArgumentException.class,
            () -> service.list("project-1", null, 20));
    }

    @Test
    void rejectsCursorThatDoesNotRepresentTheLastReturnedGeneration() {
        var first = history("generation-1", CURRENT, NOW);
        var wrongCursor = new ConfigurationHistoryPageService.Cursor(NOW.minusSeconds(1),
            "generation-2");
        var service = new ConfigurationHistoryPageService(projectId -> CURRENT,
            (projectId, after, limit) -> new ConfigurationHistoryPageService.StoredPage(
                List.of(new ConfigurationHistoryPageService.StoredItem(first, null)), wrongCursor));

        assertThrows(IllegalStateException.class,
            () -> service.list("project-1", null, 20));
    }

    @Test
    void rejectsPageThatRepeatsOrPrecedesTheRequestedCursor() {
        var after = new ConfigurationHistoryPageService.Cursor(NOW.minusSeconds(1),
            "generation-2");
        var repeated = history("generation-2", CURRENT, after.generatedAt());
        var service = new ConfigurationHistoryPageService(projectId -> CURRENT,
            (projectId, cursor, limit) -> new ConfigurationHistoryPageService.StoredPage(
                List.of(new ConfigurationHistoryPageService.StoredItem(repeated, null)), null));

        assertThrows(IllegalStateException.class,
            () -> service.list("project-1", after, 20));

        var newer = history("generation-1", CURRENT, NOW);
        var newerPage = new ConfigurationHistoryPageService(projectId -> CURRENT,
            (projectId, cursor, limit) -> new ConfigurationHistoryPageService.StoredPage(
                List.of(new ConfigurationHistoryPageService.StoredItem(newer, null)), null));
        assertThrows(IllegalStateException.class,
            () -> newerPage.list("project-1", after, 20));
    }

    private static ConfigurationGenerationHistory history(String id, PreviewBasis basis,
                                                          Instant createdAt) {
        return ConfigurationGenerationHistory.fromStoredMetadata(id, "recommendation-1",
            "preview-1", "approval-1", basis, "b".repeat(64),
            List.of(new ConfigurationGenerationHistory.FileMetadata("config/main.txt",
                PreviewFile.Action.PROPOSAL, "c".repeat(64))), createdAt);
    }
}
