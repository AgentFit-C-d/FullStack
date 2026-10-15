package com.agentfit.coreapi.persistence;

/** A's project key is VARCHAR(36); keep its exact value across B's JDBC boundary. */
public final class ProjectDbId {
    private ProjectDbId() {}

    public static String require(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 36) {
            throw new IllegalArgumentException("invalid project ID");
        }
        return value;
    }
}
