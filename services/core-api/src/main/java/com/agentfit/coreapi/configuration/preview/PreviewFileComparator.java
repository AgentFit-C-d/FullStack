package com.agentfit.coreapi.configuration.preview;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** In-memory comparison of reviewed candidates with only user-provided existing files. */
public final class PreviewFileComparator {
    private PreviewFileComparator() {}

    public static List<PreviewFile> compare(List<PreviewInputFile> generated,
                                            ExistingState existingState,
                                            List<PreviewInputFile> provided) {
        if (generated == null || existingState == null || provided == null
            || (existingState == ExistingState.PROVIDED && provided.isEmpty())
            || (existingState != ExistingState.PROVIDED && !provided.isEmpty())) {
            throw invalid("inconsistent existing state");
        }
        Map<FileKey, PreviewInputFile> candidates = index(generated);
        Map<FileKey, PreviewInputFile> originals = index(provided);
        if (!candidates.keySet().containsAll(originals.keySet())) {
            throw invalid("provided file is not a generated target");
        }

        List<PreviewFile> result = new ArrayList<>();
        for (PreviewInputFile file : generated.stream()
            .sorted(Comparator.comparing(PreviewInputFile::relativePath)
                .thenComparing(PreviewInputFile::targetKey)).toList()) {
            FileKey key = new FileKey(file.targetKey(), file.relativePath());
            PreviewInputFile original = originals.get(key);
            byte[] after = utf8(file.content());
            if (original == null) {
                result.add(new PreviewFile(file.targetKey(), file.relativePath(),
                    PreviewFile.Action.PROPOSAL, null, hash(after), file.content(), null,
                    PreviewFile.ComparisonScope.EXISTING_UNKNOWN));
            } else {
                byte[] before = utf8(original.content());
                result.add(new PreviewFile(file.targetKey(), file.relativePath(),
                    PreviewFile.Action.UPDATE, hash(before), hash(after), file.content(),
                    diff(file.relativePath(), original.content(), file.content()),
                    PreviewFile.ComparisonScope.PROVIDED_FILE));
            }
        }
        return List.copyOf(result);
    }

    private static Map<FileKey, PreviewInputFile> index(List<PreviewInputFile> files) {
        Map<FileKey, PreviewInputFile> indexed = new HashMap<>();
        Map<String, String> targetPaths = new HashMap<>();
        Map<String, String> pathTargets = new HashMap<>();
        Set<String> foldedPaths = new HashSet<>();
        for (PreviewInputFile file : files) {
            if (file == null || file.targetKey() == null || file.targetKey().isBlank()
                || file.content() == null) throw invalid("invalid file entry");
            validatePath(file.relativePath());
            utf8(file.content());
            if (PreviewSensitiveContentGuard.containsIdentifiedSecret(file.content())) {
                throw invalid("sensitive content detected");
            }
            String priorPath = targetPaths.putIfAbsent(file.targetKey(), file.relativePath());
            String priorTarget = pathTargets.putIfAbsent(file.relativePath(), file.targetKey());
            if ((priorPath != null && !priorPath.equals(file.relativePath()))
                || (priorTarget != null && !priorTarget.equals(file.targetKey()))
                || !foldedPaths.add(file.relativePath().toLowerCase(Locale.ROOT))
                || indexed.putIfAbsent(new FileKey(file.targetKey(), file.relativePath()), file) != null) {
                throw invalid("duplicate or conflicting file target/path");
            }
        }
        for (String path : foldedPaths) {
            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
                if (foldedPaths.contains(path.substring(0, slash))) {
                    throw invalid("file path is also a parent directory");
                }
            }
        }
        return indexed;
    }

    private static void validatePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
            || path.contains(":") || path.codePointCount(0, path.length()) > 200) {
            throw invalid("unsafe relative path");
        }
        utf8(path);
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")
                || part.endsWith(".") || part.endsWith(" ")
                || part.chars().anyMatch(c -> c < 32 || c == 127 || "<>\"|?*".indexOf(c) >= 0)) {
                throw invalid("unsafe relative path");
            }
            String stem = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (Set.of("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4",
                    "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3",
                    "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9").contains(stem)) {
                throw invalid("reserved file name");
            }
        }
    }

    private static byte[] utf8(String content) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(content));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new InvalidPreviewInputException("malformed UTF-16 content", exception);
        }
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String diff(String path, String before, String after) {
        if (before.equals(after)) return "";
        List<Line> oldLines = lines(before);
        List<Line> newLines = lines(after);
        int prefix = 0;
        while (prefix < Math.min(oldLines.size(), newLines.size())
            && oldLines.get(prefix).equals(newLines.get(prefix))) prefix++;
        int suffix = 0;
        while (suffix < oldLines.size() - prefix && suffix < newLines.size() - prefix
            && oldLines.get(oldLines.size() - 1 - suffix).equals(
                newLines.get(newLines.size() - 1 - suffix))) suffix++;
        int oldStart = prefix - Math.min(prefix, 3);
        int oldEnd = oldLines.size() - suffix + Math.min(suffix, 3);
        int newEnd = newLines.size() - suffix + Math.min(suffix, 3);
        int oldCount = oldEnd - oldStart;
        int newCount = newEnd - oldStart;
        StringBuilder result = new StringBuilder("--- a/").append(path).append('\n')
            .append("+++ b/").append(path).append('\n')
            .append("@@ -").append(oldCount == 0 ? oldStart : oldStart + 1).append(',')
            .append(oldCount).append(" +")
            .append(newCount == 0 ? oldStart : oldStart + 1).append(',')
            .append(newCount).append(" @@\n");
        appendLines(result, ' ', oldLines.subList(oldStart, prefix));
        appendLines(result, '-', oldLines.subList(prefix, oldLines.size() - suffix));
        appendLines(result, '+', newLines.subList(prefix, newLines.size() - suffix));
        appendLines(result, ' ', oldLines.subList(oldLines.size() - suffix, oldEnd));
        return result.toString();
    }

    private static List<Line> lines(String value) {
        if (value.isEmpty()) return List.of();
        String[] parts = value.split("\n", -1);
        int count = parts.length - (value.endsWith("\n") ? 1 : 0);
        List<Line> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(new Line(parts[index], index < parts.length - 1));
        }
        return result;
    }

    private static void appendLines(StringBuilder out, char prefix, List<Line> lines) {
        for (Line line : lines) {
            out.append(prefix).append(line.text()).append('\n');
            if (!line.terminated()) out.append("\\ No newline at end of file\n");
        }
    }

    private record Line(String text, boolean terminated) {}

    private static InvalidPreviewInputException invalid(String reason) {
        return new InvalidPreviewInputException(reason);
    }

    private record FileKey(String targetKey, String relativePath) {}

    public static final class InvalidPreviewInputException extends IllegalArgumentException {
        public InvalidPreviewInputException(String message) { super(message); }
        public InvalidPreviewInputException(String message, Throwable cause) { super(message, cause); }
    }
}
