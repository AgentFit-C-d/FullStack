package com.agentfit.coreapi.configuration.export;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;

import java.time.Instant;

/** Minimal server-stored Preview metadata; file content, originals, and Diff are excluded. */
public record StoredPreviewState(String previewId, PreviewBasis basis,
                                 String fingerprint, Instant createdAt, Instant expiresAt) {}
