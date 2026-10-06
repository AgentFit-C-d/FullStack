package com.agentfit.coreapi.configuration.preview;

import java.util.List;

/** Transient Preview files and content/selection/approval-target digests. */
public record PreviewFingerprintResult(List<PreviewFile> files, String selectionDigest,
                                       String contentDigest, String fingerprint) {
    public PreviewFingerprintResult {
        files = List.copyOf(files);
    }
}
