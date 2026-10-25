package com.agentfit.coreapi.catalog.discovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;

/** Downloads a pinned candidate to an isolated review directory, never into the live Catalog. */
public final class SkillsShCandidateFileStager {
    private SkillsShCandidateFileStager() {}

    public static SkillsShCandidateCollector.SkillPackage stage(Path snapshot,
            String externalId, Path targetDirectory, SkillsShCandidateCollector collector) {
        if (snapshot == null || externalId == null || externalId.isBlank()
            || targetDirectory == null || collector == null) {
            throw new IllegalArgumentException("snapshot, candidate, target and collector required");
        }
        SkillCandidate candidate = SkillsShCandidateSnapshot.load(snapshot).stream()
            .filter(item -> externalId.equals(item.externalId())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("candidate not found in snapshot"));
        Path target = targetDirectory.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(parent) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("review target must be a new directory");
        }

        SkillsShCandidateCollector.SkillPackage fetched = collector.download(candidate);
        Path temporary = null;
        try {
            temporary = Files.createTempDirectory(parent, ".skills-sh-review-");
            for (var file : fetched.files().entrySet()) {
                Path destination = temporary.resolve(file.getKey()).normalize();
                if (!destination.startsWith(temporary)) {
                    throw new IllegalArgumentException("unsafe Skill path");
                }
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, file.getValue(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target);
            }
            temporary = null;
            return fetched;
        } catch (IOException exception) {
            throw new IllegalStateException("candidate file staging failed", exception);
        } finally {
            if (temporary != null) {
                try (var paths = Files.walk(temporary)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ignored) { /* failed staging is never activated */ }
            }
        }
    }
}
