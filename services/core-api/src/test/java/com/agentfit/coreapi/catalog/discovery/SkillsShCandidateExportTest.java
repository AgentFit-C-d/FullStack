package com.agentfit.coreapi.catalog.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillsShCandidateExportTest {
    @TempDir Path directory;

    @Test
    void selectedPinnedCandidateExportsActualSkillAndProvenance() throws Exception {
        Path snapshot = snapshot();
        var exporter = new SkillsShCandidateExport(candidate ->
            new SkillsShCandidateCollector.SkillPackage(candidate.contentHash(),
                Map.of("SKILL.md", "# Example", "references/guide.md", "# Guide")));

        byte[] zip = exporter.export(snapshot, "owner/repo/example", "a".repeat(64));
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            Map<String, String> files = new java.util.HashMap<>();
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                files.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals("# Example", files.get(".claude/skills/example/SKILL.md"));
            assertEquals("# Guide", files.get(".claude/skills/example/references/guide.md"));
            assertTrue(files.get("AGENTFIT-SKILL-SOURCE.txt").contains("UNVERIFIED"));
            assertTrue(files.get("AGENTFIT-SKILL-SOURCE.txt").contains("https://skills.sh/owner/repo/example"));
            assertTrue(files.get("AGENTFIT-SKILL-SOURCE.txt").contains("a".repeat(64)));
            assertTrue(files.get("AGENTFIT-APPLY.md").contains(".claude/skills/example/"));
            assertTrue(files.get("AGENTFIT-APPLY.md").contains("claude --version"));
            assertTrue(files.get("AGENTFIT-APPLY.md").contains("복원"));
        }
    }

    @Test
    void unknownOrStaleSelectionNeverFetchesRemoteFiles() {
        Path snapshot = snapshot();
        AtomicInteger fetches = new AtomicInteger();
        var exporter = new SkillsShCandidateExport(candidate -> {
            fetches.incrementAndGet();
            return new SkillsShCandidateCollector.SkillPackage(candidate.contentHash(), Map.of("SKILL.md", "# Example"));
        });
        assertThrows(IllegalArgumentException.class,
            () -> exporter.export(snapshot, "other/repo/example", "a".repeat(64)));
        assertThrows(IllegalArgumentException.class,
            () -> exporter.export(snapshot, "owner/repo/example", "b".repeat(64)));
        assertEquals(0, fetches.get());
    }

    @Test
    void remoteContentHashChangeIsRejected() {
        var exporter = new SkillsShCandidateExport(candidate ->
            new SkillsShCandidateCollector.SkillPackage("b".repeat(64), Map.of("SKILL.md", "# Changed")));
        assertThrows(IllegalArgumentException.class,
            () -> exporter.export(snapshot(), "owner/repo/example", "a".repeat(64)));
    }

    @Test
    void liveSearchSelectionCanExportWithoutStaticSnapshot() throws Exception {
        var exporter = new SkillsShCandidateExport(candidate ->
            new SkillsShCandidateCollector.SkillPackage(candidate.contentHash(),
                Map.of("SKILL.md", "# Live skill")));
        byte[] zip = exporter.exportPinned("owner/repo/live-skill", "c".repeat(64));
        try (var input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            Map<String, String> files = new java.util.HashMap<>();
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                files.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals("# Live skill", files.get(".claude/skills/live-skill/SKILL.md"));
            assertTrue(files.get("AGENTFIT-SKILL-SOURCE.txt").contains("c".repeat(64)));
        }
    }

    @Test
    void liveSearchSelectionRejectsChangedHash() {
        var exporter = new SkillsShCandidateExport(candidate ->
            new SkillsShCandidateCollector.SkillPackage("d".repeat(64),
                Map.of("SKILL.md", "# Changed")));
        assertThrows(IllegalArgumentException.class,
            () -> exporter.exportPinned("owner/repo/live-skill", "c".repeat(64)));
    }

    private Path snapshot() {
        Path path = directory.resolve("candidates.json");
        SkillsShCandidateSnapshot.refresh(path, List.of(new SkillCandidate(
            "owner/repo/example", "Example", "owner/repo",
            "https://skills.sh/owner/repo/example", "a".repeat(64),
            Instant.parse("2026-10-28T00:00:00Z"))));
        return path;
    }
}
