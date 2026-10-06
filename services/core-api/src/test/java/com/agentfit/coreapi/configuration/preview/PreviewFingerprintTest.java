package com.agentfit.coreapi.configuration.preview;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewFingerprintTest {
    private static final String CATALOG_HASH = "a".repeat(64);

    private PreviewBasis basis() {
        return new PreviewBasis("project-1", 7, "profile-1", 3, "event-1", 2,
            0, 5, "release-1", CATALOG_HASH);
    }

    private PreviewFingerprintInput input(List<String> tools, List<PermissionSelection> policies,
                                          List<PreviewInputFile> generated) {
        return new PreviewFingerprintInput(basis(), "rec-1", tools, policies, "generator-1",
            generated, ExistingState.UNKNOWN, List.of());
    }

    @Test
    void equivalentOrderProducesSameDigestsAndTransientFiles() {
        PermissionSelection ask = new PermissionSelection("doc", "network", PermissionPolicy.ASK_EACH_TIME);
        PermissionSelection deny = new PermissionSelection("test", "write", PermissionPolicy.DENY);
        PreviewInputFile config = new PreviewInputFile("client", "settings/config.txt", "hello");
        PreviewInputFile guide = new PreviewInputFile("guide", "README.txt", "readme");
        PreviewFingerprintResult first = PreviewFingerprint.compute(input(
            List.of("test", "doc"), List.of(deny, ask), List.of(config, guide)));
        PreviewFingerprintResult reordered = PreviewFingerprint.compute(input(
            List.of("doc", "test"), List.of(ask, deny), List.of(guide, config)));
        assertEquals(first.selectionDigest(), reordered.selectionDigest());
        assertEquals(first.contentDigest(), reordered.contentDigest());
        assertEquals(first.fingerprint(), reordered.fingerprint());
        assertEquals(first.files(), reordered.files());
        assertTrue(first.fingerprint().matches("[0-9a-f]{64}"));
        assertThrows(UnsupportedOperationException.class, () -> first.files().clear());
    }

    @Test
    void contentOriginalBasisPolicyAndGeneratorChangesInvalidateFingerprint() {
        PreviewInputFile generated = new PreviewInputFile("client", "config.txt", "hello");
        PermissionSelection ask = new PermissionSelection("doc", "network", PermissionPolicy.ASK_EACH_TIME);
        PreviewFingerprintInput baseline = input(List.of("doc"), List.of(ask), List.of(generated));
        String initial = PreviewFingerprint.compute(baseline).fingerprint();

        assertNotEquals(initial, PreviewFingerprint.compute(input(List.of("doc"), List.of(ask),
            List.of(new PreviewInputFile("client", "config.txt", "hello\n")))).fingerprint());
        assertNotEquals(initial, PreviewFingerprint.compute(input(List.of("doc"),
            List.of(new PermissionSelection("doc", "network", PermissionPolicy.DENY)),
            List.of(generated))).fingerprint());
        assertNotEquals(initial, PreviewFingerprint.compute(new PreviewFingerprintInput(
            new PreviewBasis("project-1", 8, "profile-1", 3, "event-1", 2,
                0, 5, "release-1", CATALOG_HASH), "rec-1", List.of("doc"), List.of(ask),
            "generator-1", List.of(generated), ExistingState.UNKNOWN, List.of())).fingerprint());
        assertNotEquals(initial, PreviewFingerprint.compute(new PreviewFingerprintInput(
            basis(), "rec-1", List.of("doc"), List.of(ask), "generator-2",
            List.of(generated), ExistingState.UNKNOWN, List.of())).fingerprint());

        PreviewFingerprintInput provided = new PreviewFingerprintInput(basis(), "rec-1",
            List.of("doc"), List.of(ask), "generator-1", List.of(generated), ExistingState.PROVIDED,
            List.of(new PreviewInputFile("client", "config.txt", "old")));
        PreviewFingerprintInput changedOriginal = new PreviewFingerprintInput(basis(), "rec-1",
            List.of("doc"), List.of(ask), "generator-1", List.of(generated), ExistingState.PROVIDED,
            List.of(new PreviewInputFile("client", "config.txt", "older")));
        assertNotEquals(PreviewFingerprint.compute(provided).fingerprint(),
            PreviewFingerprint.compute(changedOriginal).fingerprint());
    }

    @Test
    void rejectsMissingConfirmationMalformedHashAndDuplicateChoices() {
        PreviewInputFile generated = new PreviewInputFile("client", "config.txt", "hello");
        PreviewBasis missingEvent = new PreviewBasis("project-1", 7, "profile-1", 3, "", 2,
            0, 5, "release-1", CATALOG_HASH);
        assertThrows(PreviewFingerprint.InvalidFingerprintInputException.class,
            () -> PreviewFingerprint.compute(new PreviewFingerprintInput(missingEvent, "rec-1",
                List.of("doc"), List.of(), "generator-1", List.of(generated),
                ExistingState.UNKNOWN, List.of())));
        PreviewBasis badHash = new PreviewBasis("project-1", 7, "profile-1", 3, "event-1", 2,
            0, 5, "release-1", "not-a-hash");
        assertThrows(PreviewFingerprint.InvalidFingerprintInputException.class,
            () -> PreviewFingerprint.compute(new PreviewFingerprintInput(badHash, "rec-1",
                List.of("doc"), List.of(), "generator-1", List.of(generated),
                ExistingState.UNKNOWN, List.of())));
        assertThrows(PreviewFingerprint.InvalidFingerprintInputException.class,
            () -> PreviewFingerprint.compute(input(List.of("doc", "doc"), List.of(), List.of(generated))));
        PermissionSelection ask = new PermissionSelection("doc", "network", PermissionPolicy.ASK_EACH_TIME);
        assertThrows(PreviewFingerprint.InvalidFingerprintInputException.class,
            () -> PreviewFingerprint.compute(input(List.of("doc"), List.of(ask, ask),
                List.of(generated))));
    }

    @Test
    void canonicalJsonMatchesIndependentlyCalculatedFixture() {
        PreviewFingerprintResult result = PreviewFingerprint.compute(input(List.of("doc"),
            List.of(new PermissionSelection("doc", "network", PermissionPolicy.ASK_EACH_TIME)),
            List.of(new PreviewInputFile("client", "config.txt", "hello"))));
        assertEquals("61b71a71d9ee98b891f92f86ff57136bbe8fc25fd3e7b550142579ebbb4eed7a",
            result.selectionDigest());
        assertEquals("90ec155580a359f9d6cc9ed26ffda0f323911c8d44dfa95818e6347ad05ca52d",
            result.contentDigest());
        assertEquals("da82fd3d4600d8d424b348431440dfca6bdb01e144b45310a7ae4957241d12bb",
            result.fingerprint());
    }

    @Test
    void inputSnapshotDoesNotChangeWhenCallerMutatesLists() {
        List<String> tools = new ArrayList<>(List.of("doc"));
        List<PreviewInputFile> generated = new ArrayList<>(
            List.of(new PreviewInputFile("client", "config.txt", "hello")));
        PreviewFingerprintInput input = new PreviewFingerprintInput(basis(), "rec-1", tools,
            List.of(), "generator-1", generated, ExistingState.UNKNOWN, List.of());
        String fingerprint = PreviewFingerprint.compute(input).fingerprint();
        tools.set(0, "other");
        generated.set(0, new PreviewInputFile("client", "config.txt", "changed"));
        assertEquals(fingerprint, PreviewFingerprint.compute(input).fingerprint());
    }

    @Test
    void refusesOversizedRegenerationInputBeforeComparisonOrHashing() {
        PreviewInputFile normal = new PreviewInputFile("client", "config.txt", "hello");
        assertThrows(IllegalArgumentException.class,
            () -> PreviewFingerprint.compute(input(List.of("doc"), List.of(),
                List.of(new PreviewInputFile("client", "config.txt", "a".repeat(100_001))))));

        PreviewFingerprintInput oversizedOriginal = new PreviewFingerprintInput(basis(), "rec-1",
            List.of("doc"), List.of(), "generator-1", List.of(normal), ExistingState.PROVIDED,
            List.of(new PreviewInputFile("client", "config.txt", "a".repeat(100_001))));
        assertThrows(IllegalArgumentException.class,
            () -> PreviewFingerprint.compute(oversizedOriginal));

        List<PreviewInputFile> oversizedBytes = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            oversizedBytes.add(new PreviewInputFile("target-" + index, "file-" + index + ".txt",
                "한".repeat(100_000)));
        }
        assertThrows(IllegalArgumentException.class,
            () -> PreviewFingerprint.compute(input(List.of("doc"), List.of(), oversizedBytes)));
    }

    @Test
    void refusesOversizedMetadataBeforeCanonicalHashing() {
        PreviewInputFile normal = new PreviewInputFile("client", "config.txt", "hello");
        assertDoesNotThrow(() -> PreviewFingerprint.compute(new PreviewFingerprintInput(basis(),
            "r".repeat(128), List.of("doc"), List.of(), "generator-1", List.of(normal),
            ExistingState.UNKNOWN, List.of())));
        assertThrows(PreviewFingerprint.LimitExceededException.class,
            () -> PreviewFingerprint.compute(new PreviewFingerprintInput(basis(), "r".repeat(129),
                List.of("doc"), List.of(), "generator-1", List.of(normal),
                ExistingState.UNKNOWN, List.of())));
        assertThrows(PreviewFingerprint.LimitExceededException.class,
            () -> PreviewFingerprint.compute(input(List.of("도".repeat(129)), List.of(),
                List.of(normal))));
        assertThrows(PreviewFingerprint.LimitExceededException.class,
            () -> PreviewFingerprint.compute(input(List.of("doc"), List.of(),
                List.of(new PreviewInputFile("t".repeat(201), "config.txt", "hello")))));
        assertThrows(PreviewFingerprint.LimitExceededException.class,
            () -> PreviewFingerprint.compute(input(List.of("doc"),
                List.of(new PermissionSelection("doc", "m".repeat(201),
                    PermissionPolicy.ASK_EACH_TIME)), List.of(normal))));
        assertThrows(PreviewFingerprint.LimitExceededException.class,
            () -> PreviewFingerprint.compute(new PreviewFingerprintInput(basis(), "rec-1",
                List.of("doc"), List.of(), "g".repeat(129), List.of(normal),
                ExistingState.UNKNOWN, List.of())));
        PreviewBasis oversizedBasis = new PreviewBasis("p".repeat(129), 7, "profile-1", 3,
            "event-1", 2, 0, 5, "release-1", CATALOG_HASH);
        assertThrows(PreviewFingerprint.LimitExceededException.class,
            () -> PreviewFingerprint.compute(new PreviewFingerprintInput(oversizedBasis, "rec-1",
                List.of("doc"), List.of(), "generator-1", List.of(normal),
                ExistingState.UNKNOWN, List.of())));
    }
}
