package com.agentfit.coreapi.recommendation;

public record EnvironmentTarget(String osFamily, String clientId, String clientVersion) {
    public boolean complete() {
        return nonblank(osFamily) && nonblank(clientId) && nonblank(clientVersion);
    }

    private static boolean nonblank(String value) {
        return value != null && !value.isBlank();
    }
}
