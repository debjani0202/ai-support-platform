package com.supportai.user.dto;

import com.supportai.user.entity.Role;

/**
 * Safe projection of support agent user details.
 * Never exposes passwords or sensitive credentials.
 */
public record AgentResponse(
        Long id,
        String name,
        String email,
        Role role
) {
}
