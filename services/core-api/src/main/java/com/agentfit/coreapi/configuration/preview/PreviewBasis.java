package com.agentfit.coreapi.configuration.preview;

/** Version basis provided by A's current, owner-checked snapshot plus B's Catalog. */
public record PreviewBasis(String projectId, long projectVersion,
                           String confirmedProfileId, long confirmedProfileVersion,
                           String confirmationEventId, long reviewVersion,
                           long developerVersion, long environmentVersion,
                           String catalogReleaseId, String catalogHash) {}
