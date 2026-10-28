package com.agentfit.coreapi.catalog.discovery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
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
        return exportCandidate(candidate);
    }

    /** A live search item carries its own pinned hash; the source is fetched again before export. */
    public byte[] exportPinned(String externalId, String contentHash) {
        if (externalId == null || contentHash == null || !contentHash.matches("[0-9a-f]{64}")
            || !externalId.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)?")) {
            throw new IllegalArgumentException("pinned candidate required");
        }
        String source = externalId.substring(0, externalId.lastIndexOf('/'));
        String slug = externalId.substring(externalId.lastIndexOf('/') + 1);
        return exportCandidate(new SkillCandidate(externalId, slug, source,
            "https://skills.sh/" + externalId, contentHash, Instant.EPOCH));
    }

    private byte[] exportCandidate(SkillCandidate candidate) {
        String contentHash = candidate.contentHash();
        String externalId = candidate.externalId();
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
                add(zip, "AGENTFIT-APPLY.md", applyInstructions(slug));
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

    private static String applyInstructions(String slug) {
        return "# Claude Code Skill 적용 안내\n\n"
            + "1. ZIP의 `.claude/skills/" + slug + "/` 폴더를 대상 프로젝트에 복사합니다. "
            + "기존 폴더가 있으면 먼저 백업하고 내용을 비교하세요.\n"
            + "2. `SKILL.md`와 함께 포함된 스크립트, 요청 권한 및 출처를 확인합니다. "
            + "이 후보의 Claude Code 버전·OS·조합 호환성은 확인되지 않았습니다.\n"
            + "3. 필요한 외부 서비스 인증은 해당 서비스의 공식 안내에 따라 사용자가 직접 진행합니다. "
            + "AgentFit ZIP에는 인증 정보가 포함되지 않습니다.\n"
            + "4. `claude --version`으로 Client 버전을 확인한 뒤 Claude Code에서 Skill 목록을 확인합니다. "
            + "외부 명령을 실행하기 전에는 표시된 내용을 검토하세요.\n"
            + "5. 문제가 생기면 복사한 `.claude/skills/" + slug + "/` 폴더를 제거하고 "
            + "백업한 기존 폴더를 복원합니다. 이 절차는 자동 설치·인증·실행 검증을 뜻하지 않습니다.\n";
    }

    private static void add(ZipOutputStream zip, String path, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
