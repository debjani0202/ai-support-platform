package com.supportai.auth.dto;

public record AuthResult(
        LoginResponse loginResponse,
        String rawRefreshToken
) {
}

