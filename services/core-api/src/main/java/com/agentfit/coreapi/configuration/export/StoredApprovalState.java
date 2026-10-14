package com.agentfit.coreapi.configuration.export;

import java.time.Instant;

/** Minimal server-stored approval metadata; the caller must load it with owner checks. */
public record StoredApprovalState(String approvalId, String previewId, String fingerprint,
                                  Instant approvedAt, Instant expiresAt) {}
