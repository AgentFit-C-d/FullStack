package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.agentfit.coreapi.configuration.preview.ExistingState;
import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.recommendation.selection.PermissionPolicy;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PreviewAssemblyLimitsTest {
    private CatalogPreviewRequest request(List<String> tools, List<PermissionSelection> policies,
                                          List<PreviewInputFile> existing) {
        return new CatalogPreviewRequest(null, "rec", null, tools, policies,
            existing.isEmpty() ? ExistingState.UNKNOWN : ExistingState.PROVIDED, existing, "v1");
    }

    private List<PreviewInputFile> files(int count, String content) {
        return IntStream.range(0, count)
            .mapToObj(index -> new PreviewInputFile("t" + index, "file-" + index + ".txt", content))
            .toList();
    }

    @Test
    void acceptsExactPerFileCodePointLimitAndRejectsOneMore() {
        PreviewAssemblyLimits.validateGenerated(files(1, "a".repeat(100_000)));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> PreviewAssemblyLimits.validateGenerated(files(1, "a".repeat(100_001))));
    }

    @Test
    void rejectsUtf8AggregateOverflowEvenWithValidCodePointCounts() {
        String emoji = "😀".repeat(100_000);
        PreviewAssemblyLimits.validateGenerated(files(2, emoji));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> PreviewAssemblyLimits.validateGenerated(files(3, emoji)));
    }

    @Test
    void rejectsTooManyGeneratedAndProvidedFiles() {
        PreviewAssemblyLimits.validateGenerated(files(20, "ok"));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> PreviewAssemblyLimits.validateGenerated(files(21, "ok")));
        PreviewAssemblyLimits.validateRequest(request(List.of("tool"), List.of(), files(20, "ok")));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> PreviewAssemblyLimits.validateRequest(request(List.of("tool"), List.of(),
                files(21, "ok"))));
    }

    @Test
    void rejectsTooManySelectedToolsOrPermissionChoices() {
        List<String> twenty = IntStream.range(0, 20).mapToObj(i -> "tool-" + i).toList();
        PreviewAssemblyLimits.validateRequest(request(twenty, List.of(), List.of()));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> PreviewAssemblyLimits.validateRequest(request(
                IntStream.range(0, 21).mapToObj(i -> "tool-" + i).toList(), List.of(), List.of())));
        List<PermissionSelection> policies = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            policies.add(new PermissionSelection("tool", "mapping-" + i, PermissionPolicy.ASK_EACH_TIME));
        }
        PreviewAssemblyLimits.validateRequest(request(List.of("tool"), policies.subList(0, 200), List.of()));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> PreviewAssemblyLimits.validateRequest(request(List.of("tool"), policies, List.of())));
    }

    @Test
    void rejectsOversizedChoiceMetadataBeforeCatalogLoading() {
        PreviewAssemblyLimits.validateRequest(new CatalogPreviewRequest(null, "r".repeat(128),
            null, List.of("tool"), List.of(), ExistingState.UNKNOWN, List.of(), "v1"));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> PreviewAssemblyLimits.validateRequest(new CatalogPreviewRequest(null,
                "r".repeat(129), null, List.of("tool"), List.of(), ExistingState.UNKNOWN,
                List.of(), "v1")));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> PreviewAssemblyLimits.validateRequest(request(List.of("t".repeat(129)),
                List.of(), List.of())));
        assertThrows(PreviewAssemblyLimits.LimitExceededException.class,
            () -> PreviewAssemblyLimits.validateRequest(request(List.of("tool"), List.of(),
                files(1, "ok").stream().map(file -> new PreviewInputFile("t".repeat(201),
                    file.relativePath(), file.content())).toList())));
    }
}
