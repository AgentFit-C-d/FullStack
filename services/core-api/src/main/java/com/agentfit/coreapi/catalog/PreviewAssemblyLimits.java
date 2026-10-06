package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.configuration.preview.PreviewInputFile;
import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import com.agentfit.coreapi.recommendation.selection.PermissionSelection;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Proposed B Preview content budget; HTTP must enforce full request-body limits separately. */
public final class PreviewAssemblyLimits {
    private static final int MAX_TOOLS = 20;
    private static final int MAX_POLICIES = 200;
    private static final int MAX_FILES = 20;
    private static final int MAX_CODE_POINTS_PER_FILE = 100_000;
    private static final int MAX_ID_CODE_POINTS = 128;
    private static final int MAX_KEY_CODE_POINTS = 200;
    private static final long MAX_CONTENT_BYTES = 1_048_576;

    private PreviewAssemblyLimits() {}

    static void validateRequest(CatalogPreviewRequest request) {
        if (request == null || request.selectedToolKeys() == null
            || request.permissionSelections() == null || request.providedFiles() == null) {
            throw new LimitExceededException("incomplete Preview input");
        }
        if (request.selectedToolKeys().size() > MAX_TOOLS
            || request.permissionSelections().size() > MAX_POLICIES) {
            throw new LimitExceededException("too many Preview choices");
        }
        PreviewBasis basis = request.basis();
        if (basis != null) {
            validateLength(basis.projectId(), MAX_ID_CODE_POINTS);
            validateLength(basis.confirmedProfileId(), MAX_ID_CODE_POINTS);
            validateLength(basis.confirmationEventId(), MAX_ID_CODE_POINTS);
            validateLength(basis.catalogReleaseId(), MAX_ID_CODE_POINTS);
        }
        validateLength(request.recommendationId(), MAX_ID_CODE_POINTS);
        validateLength(request.generatorVersion(), MAX_ID_CODE_POINTS);
        for (String toolKey : request.selectedToolKeys()) {
            validateLength(toolKey, MAX_ID_CODE_POINTS);
        }
        for (PermissionSelection policy : request.permissionSelections()) {
            if (policy != null) {
                validateLength(policy.toolKey(), MAX_ID_CODE_POINTS);
                validateLength(policy.mappingKey(), MAX_KEY_CODE_POINTS);
            }
        }
        validateFiles(request.providedFiles());
    }

    static void validateGenerated(List<PreviewInputFile> generated) {
        if (generated == null || generated.isEmpty()) {
            throw new LimitExceededException("no generated Preview files");
        }
        validateFiles(generated);
    }

    private static void validateFiles(List<PreviewInputFile> files) {
        if (files.size() > MAX_FILES) throw new LimitExceededException("too many Preview files");
        long bytes = 0;
        for (PreviewInputFile file : files) {
            if (file == null || file.content() == null) {
                throw new LimitExceededException("invalid Preview file");
            }
            validateLength(file.targetKey(), MAX_KEY_CODE_POINTS);
            String content = file.content();
            if (content.codePointCount(0, content.length()) > MAX_CODE_POINTS_PER_FILE) {
                throw new LimitExceededException("Preview file too long");
            }
            try {
                bytes += StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(content)).remaining();
            } catch (CharacterCodingException exception) {
                throw new LimitExceededException("invalid Preview text");
            }
            if (bytes > MAX_CONTENT_BYTES) {
                throw new LimitExceededException("Preview content too large");
            }
        }
    }

    private static void validateLength(String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum) {
            throw new LimitExceededException("Preview metadata too long");
        }
    }

    public static final class LimitExceededException extends IllegalArgumentException {
        public LimitExceededException(String message) { super(message); }
    }
}
