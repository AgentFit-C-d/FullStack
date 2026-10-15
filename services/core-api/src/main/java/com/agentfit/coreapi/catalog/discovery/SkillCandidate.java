package com.agentfit.coreapi.catalog.discovery;

import java.time.Instant;

/** External discovery metadata only. A candidate is never a verified CatalogTool. */
public record SkillCandidate(String externalId, String name, String source, String sourceUrl,
                             String contentHash, Instant observedAt) {
    public SkillCandidate {
        if (externalId == null
            || !externalId.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)?")
            || name == null || name.isBlank() || name.length() > 200
            || source == null || !source.equals(externalId.substring(0, externalId.lastIndexOf('/')))
            || sourceUrl == null || !sourceUrl.equals("https://skills.sh/" + externalId)
            || contentHash == null || !contentHash.matches("[0-9a-f]{64}")
            || observedAt == null) {
            throw new IllegalArgumentException("invalid skills.sh candidate");
        }
    }
}
