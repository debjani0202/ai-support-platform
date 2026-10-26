package com.supportai.auth.dto;

public record VerifyResetTokenResponse(
        boolean valid,
        String message
) {
}
