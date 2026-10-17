package com.agentfit.coreapi.domain.analysis.client;

/** A typed failure from the internal AI boundary; never treated as a normal recommendation result. */
public final class AiServiceFailureException extends RuntimeException {
    public enum Kind { TRANSPORT, CONTRACT, PROVIDER, INPUT }

    private final Kind kind;
    private final String code;

    public AiServiceFailureException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public AiServiceFailureException(Kind kind, String code, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() { return kind; }
    public String code() { return code; }
}
