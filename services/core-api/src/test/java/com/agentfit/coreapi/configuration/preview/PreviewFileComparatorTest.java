package com.agentfit.coreapi.configuration.preview;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewFileComparatorTest {
    private static final String HELLO_HASH =
        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
    private static final String WORLD_HASH =
        "486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7";

    private PreviewInputFile file(String target, String path, String content) {
        return new PreviewInputFile(target, path, content);
    }

    @Test
    void unknownAndUserSaysEmptyRemainProposalsWithoutClaimingLocalInspection() {
        for (ExistingState state : List.of(ExistingState.UNKNOWN, ExistingState.USER_SAYS_EMPTY)) {
            List<PreviewFile> result = PreviewFileComparator.compare(
                List.of(file("client", "settings/config.txt", "hello")), state, List.of());
            assertEquals(1, result.size());
            PreviewFile preview = result.getFirst();
            assertEquals(PreviewFile.Action.PROPOSAL, preview.action());
            assertEquals(PreviewFile.ComparisonScope.EXISTING_UNKNOWN, preview.comparisonScope());
            assertNull(preview.beforeHash());
            assertNull(preview.diff());
            assertEquals(HELLO_HASH, preview.afterHash());
        }
    }

    @Test
    void onlyExactlyProvidedFileGetsUpdateAndExactByteHashes() {
        List<PreviewFile> result = PreviewFileComparator.compare(List.of(
            file("client", "settings/config.txt", "world"),
            file("guide", "README.txt", "hello")), ExistingState.PROVIDED,
            List.of(file("client", "settings/config.txt", "hello")));
        assertEquals(2, result.size());
        PreviewFile update = result.stream().filter(item -> item.targetKey().equals("client"))
            .findFirst().orElseThrow();
        assertEquals(PreviewFile.Action.UPDATE, update.action());
        assertEquals(PreviewFile.ComparisonScope.PROVIDED_FILE, update.comparisonScope());
        assertEquals(HELLO_HASH, update.beforeHash());
        assertEquals(WORLD_HASH, update.afterHash());
        assertTrue(update.diff().contains("-hello"));
        assertTrue(update.diff().contains("+world"));
        PreviewFile proposal = result.stream().filter(item -> item.targetKey().equals("guide"))
            .findFirst().orElseThrow();
        assertEquals(PreviewFile.Action.PROPOSAL, proposal.action());
        assertNull(proposal.beforeHash());
    }

    @Test
    void unchangedProvidedBytesHaveEqualHashesAndNoDiff() {
        PreviewFile result = PreviewFileComparator.compare(
            List.of(file("client", "config.txt", "hello")), ExistingState.PROVIDED,
            List.of(file("client", "config.txt", "hello"))).getFirst();
        assertEquals(result.beforeHash(), result.afterHash());
        assertEquals("", result.diff());
    }

    @Test
    void rejectsInvalidStateUnmatchedFileAndKeyPathConflicts() {
        List<PreviewInputFile> generated = List.of(file("client", "config.txt", "hello"));
        List<PreviewInputFile> provided = List.of(file("client", "config.txt", "old"));
        assertThrows(PreviewFileComparator.InvalidPreviewInputException.class,
            () -> PreviewFileComparator.compare(generated, ExistingState.UNKNOWN, provided));
        assertThrows(PreviewFileComparator.InvalidPreviewInputException.class,
            () -> PreviewFileComparator.compare(generated, ExistingState.PROVIDED, List.of()));
        assertThrows(PreviewFileComparator.InvalidPreviewInputException.class,
            () -> PreviewFileComparator.compare(generated, ExistingState.PROVIDED,
                List.of(file("client", "other.txt", "old"))));
        assertThrows(PreviewFileComparator.InvalidPreviewInputException.class,
            () -> PreviewFileComparator.compare(List.of(file("one", "same.txt", "a"),
                file("two", "same.txt", "b")), ExistingState.UNKNOWN, List.of()));
        assertThrows(PreviewFileComparator.InvalidPreviewInputException.class,
            () -> PreviewFileComparator.compare(List.of(file("one", "a.txt", "a"),
                file("one", "b.txt", "b")), ExistingState.UNKNOWN, List.of()));
    }

    @Test
    void rejectsUnsafePathsAndMalformedText() {
        for (String path : List.of("../config.txt", "/absolute.txt", "C:/absolute.txt",
            "a//b", "a/./b", "a/../b", "a\\b", "a\u0000b")) {
            assertThrows(PreviewFileComparator.InvalidPreviewInputException.class,
                () -> PreviewFileComparator.compare(List.of(file("client", path, "hello")),
                    ExistingState.UNKNOWN, List.of()));
        }
        assertThrows(PreviewFileComparator.InvalidPreviewInputException.class,
            () -> PreviewFileComparator.compare(List.of(file("client", "config.txt", "\uD800")),
                ExistingState.UNKNOWN, List.of()));
    }

    @Test
    void preservesBomAndLineEndingsInHashesAndDiff() {
        PreviewFile result = PreviewFileComparator.compare(
            List.of(file("client", "config.txt", "\uFEFFhello\r\n")), ExistingState.PROVIDED,
            List.of(file("client", "config.txt", "hello\n"))).getFirst();
        assertNotEquals(result.beforeHash(), result.afterHash());
        assertEquals("\uFEFFhello\r\n", result.content());
        assertNotNull(result.diff());
    }

    @Test
    void emptyOriginalUsesValidUnifiedDiffRange() {
        PreviewFile result = PreviewFileComparator.compare(
            List.of(file("client", "config.txt", "hello")), ExistingState.PROVIDED,
            List.of(file("client", "config.txt", ""))).getFirst();
        assertTrue(result.diff().contains("@@ -0,0 +1,1 @@"));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result.beforeHash());
    }

    @Test
    void rejectsCaseInsensitivePathCollision() {
        assertThrows(PreviewFileComparator.InvalidPreviewInputException.class,
            () -> PreviewFileComparator.compare(List.of(file("one", "Settings.json", "a"),
                file("two", "settings.json", "b")), ExistingState.UNKNOWN, List.of()));
    }
}
