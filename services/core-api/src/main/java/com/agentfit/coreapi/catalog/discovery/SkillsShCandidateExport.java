package com.agentfit.coreapi.catalog.discovery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Packages a user-selected skills.sh candidate without declaring it compatible or recommended. */
public final class SkillsShCandidateExport {
    private final Function<SkillCandidate, SkillsShCandidateCollector.SkillPackage> download;

    public SkillsShCandidateExport(Function<SkillCandidate, SkillsShCandidateCollector.SkillPackage> download) {
        if (download == null) throw new IllegalArgumentException("candidate downloader required");
        this.download = download;
    }

    public byte[] export(Path snapshot, String externalId, String contentHash) {
        if (snapshot == null || externalId == null || contentHash == null
            || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("pinned candidate required");
        }
        SkillCandidate candidate = SkillsShCandidateSnapshot.load(snapshot).stream()
            .filter(item -> externalId.equals(item.externalId()) && contentHash.equals(item.contentHash()))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("candidate not found or changed"));
        SkillsShCandidateCollector.SkillPackage files = download.apply(candidate);
        if (!contentHash.equals(files.contentHash()) || !files.files().containsKey("SKILL.md")) {
            throw new IllegalArgumentException("candidate content changed");
        }
        String slug = externalId.substring(externalId.lastIndexOf('/') + 1);
        String notice = "UNVERIFIED skills.sh candidate selected by the user.\n"
            + "Source: " + candidate.sourceUrl() + "\n"
            + "Pinned content SHA-256: " + contentHash + "\n"
            + "Review SKILL.md, supporting files, source license and requested permissions before use.\n"
            + "AgentFit has not established OS/Claude Code version compatibility or combination support.\n";
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                add(zip, "AGENTFIT-SKILL-SOURCE.txt", notice);
                for (Map.Entry<String, String> file : files.files().entrySet()) {
                    String path = file.getKey();
                    if (path == null || !path.matches("[A-Za-z0-9._/-]+")
                        || path.startsWith("/") || path.contains("..")) {
                        throw new IllegalArgumentException("unsafe Skill file path");
                    }
                    add(zip, ".claude/skills/" + slug + "/" + path, file.getValue());
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("candidate ZIP creation failed", exception);
        }
    }

    private static void add(ZipOutputStream zip, String path, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
