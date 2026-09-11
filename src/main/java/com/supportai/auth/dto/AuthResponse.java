package com.supportai.auth.dto;

import com.supportai.user.entity.Role;

public record AuthResponse(
        Long userId,
        String name,
        String email,
        Role role
) {
}