package com.supportai.user.dto;

import jakarta.validation.constraints.Size;

public record CreateAgentRequestDto(
        @Size(max = 1000, message = "Application reason cannot exceed 1000 characters")
        String reason
) {
}
