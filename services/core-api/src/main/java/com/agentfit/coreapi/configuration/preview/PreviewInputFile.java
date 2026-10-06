package com.agentfit.coreapi.configuration.preview;

/** An in-memory file candidate or a file the user explicitly provided for comparison. */
public record PreviewInputFile(String targetKey, String relativePath, String content) {}
