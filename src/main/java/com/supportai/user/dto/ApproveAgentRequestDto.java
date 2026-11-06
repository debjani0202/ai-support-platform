package com.supportai.user.dto;

import jakarta.validation.constraints.Size;

public record ApproveAgentRequestDto(
        @Size(max = 1000, message = "Approval notes cannot exceed 1000 characters")
        String adminNotes
) {
}
