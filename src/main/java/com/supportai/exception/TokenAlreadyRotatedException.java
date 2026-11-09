package com.supportai.exception;

public class TokenAlreadyRotatedException extends RuntimeException {

    private final String errorCode = "TOKEN_ALREADY_ROTATED";

    public TokenAlreadyRotatedException(String message) {
        super(message);
    }

    public String getErrorCode() {
        return errorCode;
    }
}

