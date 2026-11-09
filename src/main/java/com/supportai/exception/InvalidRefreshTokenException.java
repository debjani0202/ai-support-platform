package com.supportai.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public enum Reason {
        INVALID_TOKEN,
        TOKEN_EXPIRED,
        TOKEN_REUSE_DETECTED
    }

    private final Reason reason;

    public InvalidRefreshTokenException(String message) {
        super(message);
        this.reason = Reason.INVALID_TOKEN;
    }

    public InvalidRefreshTokenException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

