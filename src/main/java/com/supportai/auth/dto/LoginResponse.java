package com.supportai.auth.dto;

import com.supportai.user.entity.Role;

public record LoginResponse(
        Long userId,
        String name,
        String email,
        Role role,
        String accessToken,
        String tokenType
) {
}