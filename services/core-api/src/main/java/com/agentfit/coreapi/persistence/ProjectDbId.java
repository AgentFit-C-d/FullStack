package com.agentfit.coreapi.persistence;

import java.util.UUID;

/** Parses A's UUID project key at B's JDBC boundary. */
public final class ProjectDbId {
    private ProjectDbId() {}

    public static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("invalid project ID");
        }
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid project ID", exception);
        }
    }

    public static UUID uuid(String value) {
        return UUID.fromString(require(value));
    }
}
