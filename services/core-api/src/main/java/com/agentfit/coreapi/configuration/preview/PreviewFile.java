package com.agentfit.coreapi.configuration.preview;

/** One transient Preview response item; never persist its content or diff. */
public record PreviewFile(String targetKey, String relativePath, Action action,
                          String beforeHash, String afterHash, String content,
                          String diff, ComparisonScope comparisonScope) {
    public enum Action { PROPOSAL, UPDATE }
    public enum ComparisonScope { EXISTING_UNKNOWN, PROVIDED_FILE }
}
